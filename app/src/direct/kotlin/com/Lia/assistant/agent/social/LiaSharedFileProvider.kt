package com.Lia.assistant.agent.social

import androidx.core.content.FileProvider

/** A second FileProvider (own name, own authority) that only serves the media shared to Lia. */
class LiaSharedFileProvider : FileProvider()
