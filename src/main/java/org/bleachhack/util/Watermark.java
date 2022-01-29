/*
 * This file is part of the BleachHack distribution (https://github.com/BleachDrinker420/BleachHack/).
 * Copyright (c) 2021 Bleach and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package org.bleachhack.util;

import net.minecraft.text.LiteralText;
import net.minecraft.text.MutableText;

public class Watermark {

	private String text1;
	private String text2;
	private int color1;
	private int color2;

	public Watermark() {
		reset(true, true);
	}

	public void reset(boolean strings, boolean colors) {
		if (strings) {
			text1 = "BleachHack-NetherFreedom";
			text2 = "";
		}

		if (colors) {
			color1 = 0xffbf30;
			color2 = 0xffafcc;
		}
	}


	public int getColor1() {
		return color1;
	}

	public int getColor2() {
		return color2;
	}

	public void setColor(int color1, int color2) {
		this.color1 = color1;
		this.color2 = color2;
	}

	public MutableText getText() {
		MutableText t1 = new LiteralText(text1).styled(s -> s.withColor(color1));
		return text2 == null ? t1 : t1.append(new LiteralText(text2).styled(s -> s.withColor(color2)));
	}

	public MutableText getShortText() {
		return new LiteralText("BH").styled(s -> s.withColor(color1));
	}
}
