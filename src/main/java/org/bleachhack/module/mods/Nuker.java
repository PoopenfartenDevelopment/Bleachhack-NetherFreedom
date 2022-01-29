/*
 * This file is part of the BleachHack distribution (https://github.com/BleachDrinker420/BleachHack/).
 * Copyright (c) 2021 Bleach and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package org.bleachhack.module.mods;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.item.PickaxeItem;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.bleachhack.event.events.EventBlockBreakCooldown;
import org.bleachhack.event.events.EventParticle;
import org.bleachhack.event.events.EventTick;
import org.bleachhack.event.events.EventWorldRender;
import org.bleachhack.eventbus.BleachSubscribe;
import org.bleachhack.module.Module;
import org.bleachhack.module.ModuleCategory;
import org.bleachhack.setting.module.*;
import org.bleachhack.util.Boxes;
import org.bleachhack.util.collections.ImmutablePairList;
import org.bleachhack.util.render.Renderer;
import org.bleachhack.util.render.color.LineColor;
import org.bleachhack.util.render.color.QuadColor;
import org.bleachhack.util.world.WorldUtils;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.FluidBlock;
import net.minecraft.client.particle.BlockDustParticle;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;

public class Nuker extends Module {

	private Set<BlockPos> renderBlocks = new HashSet<>();

	public Nuker() {
		super("Nuker", KEY_UNBOUND, ModuleCategory.WORLD, "Breaks blocks around you.",
				new SettingMode("Mode", "Normal", "SurvMulti", "Multi", "Instant").withDesc("Mining mode."),
				new SettingSlider("Multi", 1, 10, 8, 0).withDesc("How many blocks to mine at once if Multi/SurvMulti mode is selected."),
				new SettingSlider("Cooldown", 0, 4, 0, 0).withDesc("Cooldown between mining blocks (in ticks)."),
				new SettingSlider("Range", 1, 6, 5, 0).withDesc("Mining range."),
				new SettingSlider("Height", 1, 6, 3, 0).withDesc("Mining height."),
				new SettingMode("Sort", "Closest", "Furthest", "Softest", "Hardest", "None").withDesc("Which order to mine blocks in."),
				new SettingToggle("Filter", false).withDesc("Filters certain blocks.").withChildren(
						new SettingMode("Mode", "Blacklist", "Whitelist").withDesc("How to handle the list."),
						new SettingBlockList("Edit Blocks", "Edit Filtered Blocks").withDesc("Edit the filtered blocks.")),
				new SettingToggle("Raycast", true).withDesc("Only mines blocks you can see."),
				new SettingToggle("Flatten", true).withDesc("Flatten the area around you.").withChildren(
						new SettingToggle("Speedy", false).withDesc("Bypasses ecme anticheat and allows you to go really fast").withChildren(
								new SettingSlider("Speed", 0.15, 1, 0.27, 2).withDesc("Speed"))),
				new SettingRotate(false).withDesc("Rotate to blocks that you're mining."),
				new SettingToggle("Pick Only", false).withDesc("Only allows pickaxe for mining."),
				new SettingToggle("NoParticles", true).withDesc("Removes block breaking particles."),
				new SettingToggle("Highlight", false).withDesc("Highlights the blocks you're currently mining.").withChildren(
						new SettingMode("Mode", "Opacity", "Expand").withDesc("How to show the mining progress."),
						new SettingColor("Color", 255, 128, 128).withDesc("The color of the highlight.")),
				new SettingToggle("RangeHighlight", false).withDesc("Highlights the range you can mine.").withChildren(
						new SettingSlider("Width", 0.1, 10, 3, 1).withDesc("The width of the lines."),
						new SettingColor("Color", 255, 0, 0).withDesc("The color of the highlight.")));
	}

	@Override
	public void onDisable(boolean inWorld) {
		renderBlocks.clear();

		super.onDisable(inWorld);
	}

	@BleachSubscribe
	public void onTick(EventTick event) {
		renderBlocks.clear();

		double range = getSetting(3).asSlider().getValue();
		double height = getSetting(4).asSlider().getValue();

		ImmutablePairList<BlockPos, Pair<Vec3d, Direction>> blocks = new ImmutablePairList<>();

		// Add blocks around player
		SettingToggle filterToggler = getSetting(6).asToggle();
		for (int x = MathHelper.ceil(range); x >= MathHelper.floor(-range); x--) {
			for (int y = (int) height - 2; y >= (getSetting(8).asToggle().getState() ? -1 : (int) -height + 1); y--) {
				for (int z = MathHelper.ceil(range); z >= MathHelper.floor(-range); z--) {
					BlockPos pos = new BlockPos(mc.player.getEyePos().add(x, y, z));

					double distTo = MathHelper.absMax(MathHelper.absMax(mc.player.getX() - (pos.getX() + 0.5), mc.player.getEyeY() - (pos.getY() + 0.5)), mc.player.getZ() - (pos.getZ() + 0.5));

					BlockState state = mc.world.getBlockState(pos);
					if (distTo - 0.5 > getSetting(3).asSlider().getValue() || state.isAir() || state.getBlock() instanceof FluidBlock)
						continue;

					if (filterToggler.getState()) {
						boolean contains = filterToggler.getChild(1).asList(Block.class).contains(state.getBlock());

						if ((filterToggler.getChild(0).asMode().getMode() == 0 && contains)
								|| (filterToggler.getChild(0).asMode().getMode() == 1 && !contains)) {
							continue;
						}
					}

					Pair<Vec3d, Direction> vec = getBlockAngle(pos);

					if (vec != null) {
						blocks.add(pos, vec);
					} else if (!getSetting(7).asToggle().getState()) {
						blocks.add(pos, Pair.of(Vec3d.ofCenter(pos), Direction.UP));
					}
				}
			}
		}

		if (blocks.isEmpty())
			return;

		blocks.sortByKey(getBlockOrderComparator());

		int broken = 0;
		for (ImmutablePair<BlockPos, Pair<Vec3d, Direction>> pos : blocks) {
			float breakingDelta = mc.world.getBlockState(pos.getKey()).calcBlockBreakingDelta(mc.player, mc.world, pos.getKey());

			// Unbreakable block
			if (mc.interactionManager.getCurrentGameMode().isSurvivalLike() && breakingDelta == 0) {
				continue;
			}

			if (getSetting(0).asMode().getMode() == 1 && breakingDelta <= 1f && broken > 0) {
				return;
			}

			if (getSetting(9).asRotate().getState()) {
				Vec3d v = pos.getValue().getLeft();
				WorldUtils.facePosAuto(v.x, v.y, v.z, getSetting(9).asRotate());
			}

			if(!(mc.player.getMainHandStack().getItem() instanceof PickaxeItem) && getSetting(10).asToggle().getState()){
				return;
			}

			mc.interactionManager.updateBlockBreakingProgress(pos.getKey(), pos.getValue().getRight());
			renderBlocks.add(pos.getKey());
			mc.player.swingHand(Hand.MAIN_HAND);

			broken++;
			if (getSetting(0).asMode().getMode() == 0
					|| (getSetting(0).asMode().getMode() == 1 && breakingDelta <= 1f)
					|| (getSetting(0).asMode().getMode() == 1 && broken >= getSetting(1).asSlider().getValueInt())
					|| (getSetting(0).asMode().getMode() == 2 && broken >= getSetting(1).asSlider().getValueInt())) {
				return;
			}
		}
	}

	@BleachSubscribe
	public void onWorldRender(EventWorldRender.Post event) {
		if (getSetting(12).asToggle().getState()) {
			int[] color = getSetting(12).asToggle().getChild(1).asColor().getRGBArray();

			float breakingProgress = mc.interactionManager.currentBreakingProgress;

			for (BlockPos pos: renderBlocks) {
				VoxelShape shape = mc.world.getBlockState(pos).getOutlineShape(mc.world, pos);

				if (!shape.isEmpty()) {
					if (getSetting(12).asToggle().getChild(0).asMode().getMode() == 0) {
						Renderer.drawBoxBoth(shape.getBoundingBox().offset(pos),
								QuadColor.single(color[0], color[1], color[2], (int) (breakingProgress * 200)), 2.5f);
					} else {
						Renderer.drawBoxBoth(Boxes.multiply(shape.getBoundingBox().offset(pos), breakingProgress),
								QuadColor.single(color[0], color[1], color[2], 128), 2.5f);
					}
				}
			}
		}

		if (getSetting(13).asToggle().getState()) {
			Vec3d pos = mc.player.getPos().subtract(Renderer.getInterpolationOffset(mc.player));
			double range = getSetting(3).asSlider().getValue();
			int color = 0xff000000 | getSetting(13).asToggle().getChild(1).asColor().getRGB();
			float width = getSetting(13).asToggle().getChild(0).asSlider().getValueFloat();

			Renderer.drawBoxOutline(new Box(pos, pos).expand(range, 0, range), QuadColor.single(color), width,
					Direction.NORTH, Direction.WEST, Direction.SOUTH, Direction.EAST, Direction.DOWN);
		}
	}

	@BleachSubscribe
	public void onParticle(EventParticle.Normal event) {
		if (event.getParticle() instanceof BlockDustParticle && getSetting(11).asToggle().getState()) {
			event.setCancelled(true);
		}
	}

	@BleachSubscribe
	public void onBlockBreakCooldown(EventBlockBreakCooldown event) {
		event.setCooldown(getSetting(2).asSlider().getValueInt());
	}

	private Pair<Vec3d, Direction> getBlockAngle(BlockPos pos) {
		for (Direction d: Direction.values()) {
			if (!mc.world.getBlockState(pos.offset(d)).isFullCube(mc.world, pos.offset(d))) {
				Vec3d vec = WorldUtils.getLegitLookPos(pos, d, true, 5);

				if (vec != null) {
					return Pair.of(vec, d);
				}
			}
		}

		return null;
	}

	private Comparator<BlockPos> getBlockOrderComparator() {
		// Comparator that moves the block under the player to last
		// so it doesn't mine itself down without clearing everything above first
		Comparator<BlockPos> keepBlockUnderComparator = Comparator.comparing(new BlockPos(mc.player.getPos().add(0, -0.8, 0))::equals);

		Comparator<BlockPos> distComparator = Comparator.comparingDouble(b -> mc.player.getEyePos().distanceTo(Vec3d.ofCenter(b)));
		Comparator<BlockPos> hardnessComparator = Comparator.comparing(b -> mc.world.getBlockState(b).getHardness(mc.world, b));

		return switch (getSetting(5).asMode().getMode()) {
			case 0 -> keepBlockUnderComparator.thenComparing(distComparator);
			case 1 -> keepBlockUnderComparator.thenComparing(distComparator.reversed());
			case 2 -> keepBlockUnderComparator.thenComparing(hardnessComparator);
			case 3 -> keepBlockUnderComparator.thenComparing(hardnessComparator.reversed());
			default -> keepBlockUnderComparator;
		};
	}
}
