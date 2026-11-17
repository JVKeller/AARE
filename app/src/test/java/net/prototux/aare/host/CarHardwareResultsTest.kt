package net.prototux.aare.host

import aap_protobuf.service.sensorsource.message.SensorTypeOuterClass.SensorType
import android.location.Location
import androidx.car.app.hardware.common.CarValue
import androidx.car.app.hardware.info.CarHardwareLocation
import net.prototux.aare.car.CarData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CarHardwareResultsTest {
    private val withGps = CarData.State(connected = true, advertised = setOf(SensorType.SENSOR_LOCATION))
    private val type = CarHardwareResults.CAR_LOCATION

    private fun status(result: Any?) = (result as CarHardwareLocation).location.status

    @Test
    fun aFixIsSent() {
        assertEquals(CarValue.STATUS_SUCCESS, status(CarHardwareResults.build(type, withGps, Location("car"), null)))
    }

    @Test
    fun noFixYetIsNotYetRatherThanUnsupported() {
        // "Unsupported" makes CoMaps stop listening for the whole drive.
        assertEquals(CarValue.STATUS_UNAVAILABLE, status(CarHardwareResults.build(type, withGps, null, null)))
    }

    @Test
    fun aCarWithoutGpsIsUnsupported() {
        assertNull(CarHardwareResults.build(type, CarData.State(connected = true), null, null))
    }

    @Test
    fun switchOffIsUnsupportedEvenWithAFix() {
        assertNull(CarHardwareResults.build(type, withGps, Location("car"), null, locationShared = false))
    }
}
