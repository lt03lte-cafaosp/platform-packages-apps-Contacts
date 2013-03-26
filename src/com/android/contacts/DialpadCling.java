/*
 * Copyright (c) 2013, The Linux Foundation. All Rights Reserved.
Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above
      copyright notice, this list of conditions and the following
      disclaimer in the documentation and/or other materials provided
      with the distribution.
    * Neither the name of The Linux Foundation, Inc. nor the names of its
      contributors may be used to endorse or promote products derived
      from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.contacts;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.FocusFinder;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.FrameLayout;
import android.app.Activity;
import com.android.contacts.R;

public class DialpadCling extends FrameLayout {

    public static final String DIALPAD_CLING_DISMISSED_KEY = "cling.dialpad.dismissed";

    private Activity mActivity;
    private boolean mIsInitialized;
    private Drawable mBackground;
    private Drawable mPunchThroughGraphic;
    private Drawable mHandTouchGraphic;
    private int mPunchThroughGraphicCenterRadius;
    private int mButtonBarHeight;
    private float mRevealRadius;

    private Paint mErasePaint;

    public DialpadCling(Context context) {
        this(context, null, 0);
    }

    public DialpadCling(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DialpadCling(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void init(Activity activity) {
        if (!mIsInitialized) {
            mActivity = activity;

            Resources r = getContext().getResources();

            mPunchThroughGraphic = r.getDrawable(R.drawable.cling);
            mPunchThroughGraphicCenterRadius =
                r.getDimensionPixelSize(R.dimen.clingPunchThroughGraphicCenterRadius);
            mRevealRadius = r.getDimensionPixelSize(R.dimen.reveal_radius) * 1f;
            mButtonBarHeight = r.getDimensionPixelSize(R.dimen.button_bar_height);

            mErasePaint = new Paint();
            mErasePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.MULTIPLY));
            mErasePaint.setColor(0xFFFFFF);
            mErasePaint.setAlpha(0);

            mIsInitialized = true;
        }
    }

    public void cleanup() {
        mBackground = null;
        mPunchThroughGraphic = null;
        mHandTouchGraphic = null;
        mIsInitialized = false;
    }

    private int[] getPunchThroughPositions() {
        return new int[]{getMeasuredWidth() / 2, getMeasuredHeight() - (mButtonBarHeight / 2)};
    }


    public View findViewToTakeAccessibilityFocusFromHover(View child, View descendant) {
        if (descendant.includeForAccessibility()) {
            return descendant;
        }
        return null;
    }

    @Override
    public View focusSearch(int direction) {
        return this.focusSearch(null, direction);
    }

    @Override
    public View focusSearch(View focused, int direction) {
        return FocusFinder.getInstance().findNextFocus(this, focused, direction);
    }

    @Override
    public boolean onHoverEvent(MotionEvent event) {
        return true;
    }

    @Override
    public boolean onTouchEvent(android.view.MotionEvent event) {
        int[] positions = getPunchThroughPositions();
        for (int i = 0; i < positions.length; i += 2) {
            double diff = Math.sqrt(Math.pow(event.getX() - positions[i], 2) +
                Math.pow(event.getY() - positions[i + 1], 2));
            if (diff < mRevealRadius) {
                return false;
            }
        }
        return true;
    };

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (mIsInitialized) {
            DisplayMetrics metrics = new DisplayMetrics();
            mActivity.getWindowManager().getDefaultDisplay().getMetrics(metrics);

            // Initialize the draw buffer (to allow punching through)
            Bitmap b = Bitmap.createBitmap(getMeasuredWidth(), getMeasuredHeight(),
                    Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);

            // Draw the background
            if (mBackground == null) {
                mBackground = getResources().getDrawable(R.drawable.bg_cling1);
            }
            if (mBackground != null) {
                mBackground.setBounds(0, 0, getMeasuredWidth(), getMeasuredHeight());
                mBackground.draw(c);
            } else {
                c.drawColor(0x99000000);
            }

            int cx = -1;
            int cy = -1;
            float scale = mRevealRadius / mPunchThroughGraphicCenterRadius;
            int dw = (int) (scale * mPunchThroughGraphic.getIntrinsicWidth());
            int dh = (int) (scale * mPunchThroughGraphic.getIntrinsicHeight());

            // Determine where to draw the punch through graphic
            int[] positions = getPunchThroughPositions();
            for (int i = 0; i < positions.length; i += 2) {
                cx = positions[i];
                cy = positions[i + 1];
                if (cx > -1 && cy > -1) {
                    c.drawCircle(cx, cy, mRevealRadius, mErasePaint);
                    mPunchThroughGraphic.setBounds(cx - dw/2, cy - dh/2, cx + dw/2, cy + dh/2);
                    mPunchThroughGraphic.draw(c);
                }
            }

            canvas.drawBitmap(b, 0, 0, null);
            c.setBitmap(null);
            b = null;
        }

        // Draw the rest of the cling
        super.dispatchDraw(canvas);
    };
}
