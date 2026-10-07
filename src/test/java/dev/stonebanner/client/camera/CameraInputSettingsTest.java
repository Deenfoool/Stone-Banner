package dev.stonebanner.client.camera;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CameraInputSettingsTest {
    @Test void defaultsPreserveOriginalRotationAndZoom() {
        assertEquals(14.5F, CameraInputSettings.yaw(10, 10 * .45, 1));
        assertEquals(39.5F, CameraInputSettings.pitch(35, 10 * .45, 1, false));
        assertEquals(7, CameraInputSettings.zoom(8, 1, 1));
    }

    @Test void sensitivityScalesBothDirections() {
        assertEquals(18F, CameraInputSettings.yaw(10, 4, 2));
        assertEquals(2F, CameraInputSettings.yaw(10, -4, 2));
        assertEquals(37F, CameraInputSettings.pitch(35, 4, .5, false));
        assertEquals(10, CameraInputSettings.zoom(8, -1, 2));
    }

    @Test void inversionOnlyChangesVerticalRotation() {
        assertEquals(31F, CameraInputSettings.pitch(35, 4, 1, true));
        assertEquals(39F, CameraInputSettings.pitch(35, -4, 1, true));
    }

    @Test void rotationWrapsAndPitchAndZoomRemainBounded() {
        assertEquals(-175F, CameraInputSettings.yaw(175, 10, 1));
        assertEquals(175F, CameraInputSettings.yaw(-175, -10, 1));
        assertEquals(80F, CameraInputSettings.pitch(35, 1000, 4, false));
        assertEquals(-15F, CameraInputSettings.pitch(35, 1000, 4, true));
        assertEquals(2, CameraInputSettings.zoom(8, 1000, 4));
        assertEquals(24, CameraInputSettings.zoom(8, -1000, 4));
    }

    @Test void zeroInputKeepsCameraUnchanged() {
        assertEquals(10F, CameraInputSettings.yaw(10, 0, 4));
        assertEquals(35F, CameraInputSettings.pitch(35, 0, 4, true));
        assertEquals(8, CameraInputSettings.zoom(8, 0, 4));
    }
}
