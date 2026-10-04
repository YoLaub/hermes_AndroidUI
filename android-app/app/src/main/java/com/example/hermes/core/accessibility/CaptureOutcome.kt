package com.example.hermes.core.accessibility

import com.example.hermes.core.mobilecontrol.MobileScreenData
import com.example.hermes.core.mobilecontrol.MobileScreenshot

/** Result of a screenshot request: the encoded image with the observation taken right after it, or a failure code. */
sealed interface CaptureOutcome {
    class Success(val shot: MobileScreenshot, val screenData: MobileScreenData) : CaptureOutcome
    data class Failure(val code: String, val message: String) : CaptureOutcome
}
