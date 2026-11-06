package com.idlebosses;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MiniBossSize
{
	SMALL("Small", 1.0),
	MEDIUM("Medium", 1.5),
	LARGE("Large", 2.2);

	private final String label;
	// How many tiles wide the mini boss is, whatever the real boss's size.
	private final double tiles;

	@Override
	public String toString()
	{
		return label;
	}
}
