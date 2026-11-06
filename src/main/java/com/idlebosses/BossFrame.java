package com.idlebosses;

import lombok.RequiredArgsConstructor;

// One animation frame of a boss, flattened to 2D triangles in drawing order (furthest first), in model units.
// Never changed once made, so it can be handed to the Swing thread.
@RequiredArgsConstructor
final class BossFrame
{
	// Three corners a triangle.
	final float[] x;
	final float[] y;
	final int[] rgb;
	final int triangles;
	final float minX;
	final float maxX;
	final float minY;
	final float maxY;
}
