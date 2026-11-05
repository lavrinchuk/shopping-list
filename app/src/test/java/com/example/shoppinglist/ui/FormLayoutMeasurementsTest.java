package com.example.shoppinglist.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FormLayoutMeasurementsTest {
    @Test
    public void narrowWidthStacksActions() {
        assertTrue(FormLayoutMeasurements.shouldStackActions(343, 1f, 1f));
        assertFalse(FormLayoutMeasurements.shouldStackActions(344, 1f, 1f));
    }

    @Test
    public void dimensionsAreConvertedFromPixelsToDp() {
        assertTrue(FormLayoutMeasurements.shouldStackActions(686, 2f, 1f));
        assertFalse(FormLayoutMeasurements.shouldStackActions(688, 2f, 1f));
    }

    @Test
    public void largerFontsNeedMoreHorizontalSpace() {
        assertTrue(FormLayoutMeasurements.shouldStackActions(499, 1f, 1.5f));
        assertFalse(FormLayoutMeasurements.shouldStackActions(500, 1f, 1.5f));
    }

    @Test
    public void smallerFontsDoNotReduceMinimumWidth() {
        assertTrue(FormLayoutMeasurements.shouldStackActions(343, 1f, 0.8f));
        assertFalse(FormLayoutMeasurements.shouldStackActions(344, 1f, 0.8f));
    }

    @Test
    public void tallFormIsCappedAtSixtyPercentOfAvailableHeight() {
        assertEquals(600, FormLayoutMeasurements.cappedScrollHeight(800, 1000));
    }

    @Test
    public void shortFormUsesItsContentHeight() {
        assertEquals(240, FormLayoutMeasurements.cappedScrollHeight(240, 1000));
    }

    @Test
    public void formNeverGetsNegativeHeight() {
        assertEquals(0, FormLayoutMeasurements.cappedScrollHeight(100, -20));
    }
}
