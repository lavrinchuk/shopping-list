package com.example.shoppinglist.ui;

import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import androidx.core.widget.NestedScrollView;
import com.example.shoppinglist.R;

/**
 * Keeps the item form readable on narrow screens and limits its height so the list stays visible.
 *
 * <p>The listener belongs to the Activity's view tree; no ViewModel or lifecycle state is held.
 */
public final class AdaptiveFormLayoutController {
    private static final int ADD_BUTTON_SIZE_DP = 56;
    private static final int HORIZONTAL_BUTTON_MARGIN_DP = 12;
    private static final int VERTICAL_BUTTON_MARGIN_DP = 8;

    private final View root;
    private final View toolbar;
    private final LinearLayout actions;
    private final View quantity;
    private final View addButton;
    private final NestedScrollView formScroll;

    public AdaptiveFormLayoutController(View root) {
        this.root = root;
        toolbar = root.findViewById(R.id.heading);
        actions = root.findViewById(R.id.form_actions);
        quantity = root.findViewById(R.id.quantity_layout);
        addButton = root.findViewById(R.id.add);
        formScroll = root.findViewById(R.id.form_scroll);
    }

    public void attach() {
        root.addOnLayoutChangeListener(
                (view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                        updateLayout());
    }

    private void updateLayout() {
        float density = root.getResources().getDisplayMetrics().density;
        float fontScale = root.getResources().getConfiguration().fontScale;
        int availableWidth =
                root.getWidth() - root.getPaddingLeft() - root.getPaddingRight();

        updateActionOrientation(availableWidth, density, fontScale);
        updateFormScrollHeight(availableWidth);
    }

    private void updateActionOrientation(int availableWidth, float density, float fontScale) {
        boolean stacked = FormLayoutMeasurements.shouldStackActions(
                availableWidth, density, fontScale);
        int orientation = stacked ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL;
        if (actions.getOrientation() == orientation) {
            return;
        }

        actions.setOrientation(orientation);
        quantity.setLayoutParams(
                new LinearLayout.LayoutParams(
                        stacked ? ViewGroup.LayoutParams.MATCH_PARENT : 0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        stacked ? 0 : 1));

        int diameter = Math.round(ADD_BUTTON_SIZE_DP * density);
        LinearLayout.LayoutParams buttonParams =
                new LinearLayout.LayoutParams(diameter, diameter);
        buttonParams.setMarginStart(
                stacked ? 0 : Math.round(HORIZONTAL_BUTTON_MARGIN_DP * density));
        buttonParams.topMargin =
                stacked ? Math.round(VERTICAL_BUTTON_MARGIN_DP * density) : 0;
        addButton.setLayoutParams(buttonParams);
    }

    private void updateFormScrollHeight(int availableWidth) {
        int availableHeight =
                root.getHeight()
                        - root.getPaddingTop()
                        - root.getPaddingBottom()
                        - toolbar.getHeight();

        View content = formScroll.getChildAt(0);
        content.measure(
                View.MeasureSpec.makeMeasureSpec(
                        Math.max(0, availableWidth), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));

        int targetHeight =
                FormLayoutMeasurements.cappedScrollHeight(
                        content.getMeasuredHeight(), availableHeight);
        ViewGroup.LayoutParams scrollParams = formScroll.getLayoutParams();
        if (scrollParams.height != targetHeight) {
            scrollParams.height = targetHeight;
            formScroll.setLayoutParams(scrollParams);
        }
    }
}
