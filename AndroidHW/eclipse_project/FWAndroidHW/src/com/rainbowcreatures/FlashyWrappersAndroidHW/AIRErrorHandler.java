package com.rainbowcreatures.FlashyWrappersAndroidHW;

import java.io.PrintWriter;
import java.io.StringWriter;

public class AIRErrorHandler {
	public static void handle(Throwable t) {		
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		t.printStackTrace(pw);
		String s = sw.toString(); // stack trace as a string
		if (FlashyWrappers.currentAIRContext != null) {
			FlashyWrappers.currentAIRContext.dispatchStatusEventAsync("error", s);
		}
		t.printStackTrace();
	}
}
