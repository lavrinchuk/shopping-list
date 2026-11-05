package com.example.shoppinglist.ui;

/** Pure layout calculations, independent of Android views and usable in JVM tests. */
final class FormLayoutMeasurements {
    private static final int FORM_HORIZONTAL_PADDING_DP = 32;
    private static final int HORIZONTAL_ACTIONS_MIN_WIDTH_DP = 312;
    private static final float MAX_FORM_HEIGHT_FRACTION = 0.60f;

    private FormLayoutMeasurements() {}

    static boolean shouldStackActions(int availableWidthPx, float density, float fontScale) {
        return availableWidthPx / density
                < FORM_HORIZONTAL_PADDING_DP
                        + HORIZONTAL_ACTIONS_MIN_WIDTH_DP * Math.max(1f, fontScale);
    }

    static int cappedScrollHeight(int contentHeightPx, int availableHeightPx) {
        return Math.min(
                contentHeightPx,
                Math.max(0, Math.round(availableHeightPx * MAX_FORM_HEIGHT_FRACTION)));
    }
}
