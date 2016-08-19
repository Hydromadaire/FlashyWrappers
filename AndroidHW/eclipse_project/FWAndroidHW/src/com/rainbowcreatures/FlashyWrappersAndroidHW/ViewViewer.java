package com.rainbowcreatures.FlashyWrappersAndroidHW;

import android.app.Activity;
import android.graphics.Canvas;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;

public class ViewViewer {

	public static Activity activity;
	
	public static View debugViewIds(View view, String logtag) {
		Log.v(logtag, "traversing: " + view.getClass().getSimpleName() + ", id: " + view.getId());
		if (view.getParent() != null && (view.getParent() instanceof ViewGroup)) {
			return debugViewIds((View)view.getParent(), logtag);
		}
		else {
			debugChildViewIds(view, logtag, 0);
			return view;
		}
	}

	private static void debugChildViewIds(View view, String logtag, int spaces) {
		if (view instanceof ViewGroup) {
			ViewGroup group = (ViewGroup)view;
			for (int i = 0; i < group.getChildCount(); i++) {
				final View child = group.getChildAt(i);
				Log.v(logtag, padString("view(layer type " + child.getLayerType() + "): " + child.getClass().getName() + "(" + child.getId() + ")", spaces));
				debugChildViewIds(child, logtag, spaces + 1);
			}
		}
	}
	
	public static View findSurfaceView(View view) {		
		if (view.getParent() != null && (view.getParent() instanceof ViewGroup)) {
			return findSurfaceView((View)view.getParent());
		}
		else {
			return findChildSurfaceViewId(view);			
		}
	}

	private static View findChildSurfaceViewId(View view) {
		if (view instanceof ViewGroup) {
			ViewGroup group = (ViewGroup)view;
			for (int i = 0; i < group.getChildCount(); i++) {
				View child = group.getChildAt(i);	
				boolean isSurfaceView = SurfaceView.class.isAssignableFrom(child.getClass());
				if (isSurfaceView) {
					return child;
				}
				View childRes = findChildSurfaceViewId(child);
				if (childRes != null) return childRes;
			}
		}
		return null;
	}
	
	
	public static View drawWithoutSurfaceView(View view, Canvas c) {		
		if (view.getParent() != null && (view.getParent() instanceof ViewGroup)) {
			return drawWithoutSurfaceView((View)view.getParent(), c);
		}
		else {
			return drawChildWithoutSurfaceView(view, c);			
		}
	}

	private static View drawChildWithoutSurfaceView(View view, Canvas c) {
		if (view instanceof ViewGroup) {
			ViewGroup group = (ViewGroup)view;
			for (int i = 0; i < group.getChildCount(); i++) {
				View child = group.getChildAt(i);	
				boolean isSurfaceView = SurfaceView.class.isAssignableFrom(child.getClass());
				View childRes = drawChildWithoutSurfaceView(child, c);
				if (childRes != null) {
					return childRes;
				} else {
					// draw only the bottom most children I guess
					if (!isSurfaceView) child.draw(c);					
				}
			}
		}
		return null;
	}

	

	private static String padString(String str, int noOfSpaces) {
		if (noOfSpaces <= 0) {
			return str;
		}
		StringBuilder builder = new StringBuilder(str.length() + noOfSpaces);
		for (int i = 0; i < noOfSpaces; i++) {
			builder.append(' ');
		}
		return builder.append(str).toString();
	}

}