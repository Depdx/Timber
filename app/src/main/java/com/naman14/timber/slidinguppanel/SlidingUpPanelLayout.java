package com.naman14.timber.slidinguppanel;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.os.Parcelable;
import androidx.core.content.ContextCompat;
import androidx.core.view.MotionEventCompat;
import androidx.core.view.ViewCompat;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;

import com.naman14.timber.R;

public class SlidingUpPanelLayout extends ViewGroup {
    /**
     * Drawable used to draw the shadow between panes.
     */
    private final Drawable mShadowDrawable;
    private final ViewDragHelper mDragHelper;
    /**
     * True if the collapsed panel should be dragged up.
     */
    private boolean mIsSlidingUp;

    /**
     * If provided, the panel can be dragged by only this view. Otherwise, the entire panel can be
     * used for dragging.
     */
    private View mDragView;
    /**
     * The child view that can slide, if any.
     */
    private View mSlideableView;
    /**
     * The main view
     */
    private View mMainView;
    private SlideState mSlideState = SlideState.COLLAPSED;
    /**
     * How far the panel is offset from its expanded position.
     * range [0, 1] where 0 = collapsed, 1 = expanded.
     */
    private float mSlideOffset;
    /**
     * How far in pixels the slideable panel may move.
     */
    private int mSlideRange;
    /**
     * A panel view is locked into internal scrolling or another condition that
     * is preventing a drag.
     */
    private boolean mIsUnableToDrag;

    private float mInitialMotionX;
    private float mInitialMotionY;
    public PanelSlideListener panelSlideListener;
    /**
     * Stores whether or not the pane was expanded the last time it was slideable.
     * If expand/collapse operations are invoked this state is modified. Used by
     * instance state save/restore.
     */
    private boolean mFirstLayout = true;

    private StyledAttributes mStyledAttributes;
    private static class StyledAttributes {
        public int panelHeight;
        public int slidePanelOffset;
        public int shadowHeight;
        public int parallaxOffset;
        public boolean directOffset;
        public int coveredFadeColor;
        public int dragViewResId;
        public boolean dragViewClickable;
        public boolean overlayContent;
        public float anchorPoint;
        public  SlidingUpPanelLayout.SlideState slideState;

        public StyledAttributes(TypedArray ta){
            panelHeight = ta.getDimensionPixelSize(R.styleable.SlidingUpPanelLayout_panelHeight, -1);
            slidePanelOffset = ta.getDimensionPixelSize(R.styleable.SlidingUpPanelLayout_slidePanelOffset, 0);
            shadowHeight = ta.getDimensionPixelSize(R.styleable.SlidingUpPanelLayout_shadowHeight, -1);
            parallaxOffset = ta.getDimensionPixelSize(R.styleable.SlidingUpPanelLayout_paralaxOffset, -1);
            directOffset = ta.getBoolean(R.styleable.SlidingUpPanelLayout_directOffset, false);
            coveredFadeColor = ta.getColor(R.styleable.SlidingUpPanelLayout_fadeColor, 0x99000000);
            dragViewResId = ta.getResourceId(R.styleable.SlidingUpPanelLayout_dragView, -1);
            dragViewClickable = ta.getBoolean(R.styleable.SlidingUpPanelLayout_dragViewClickable, true);
            overlayContent = ta.getBoolean(R.styleable.SlidingUpPanelLayout_overlay, false);
            anchorPoint = ta.getFloat(R.styleable.SlidingUpPanelLayout_anchorPoint, 1.0f);
            slideState = SlideState.values()[ta.getInt(R.styleable.SlidingUpPanelLayout_initialState, SlideState.COLLAPSED.ordinal())];
        }
    }

    public SlidingUpPanelLayout(Context context) {
        this(context, null);
    }

    public SlidingUpPanelLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SlidingUpPanelLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        if (isInEditMode()) {
            mShadowDrawable = null;
            mDragHelper = null;
            return;
        }

        /**
         * Minimum velocity that will be detected as a fling
         */
        if (attrs != null) {
            TypedArray defAttrs = context.obtainStyledAttributes(attrs, new int[]{
                    android.R.attr.gravity
            });

            if (defAttrs != null) {
                int gravity = defAttrs.getInt(0, Gravity.NO_GRAVITY);
                if (gravity != Gravity.TOP && gravity != Gravity.BOTTOM) {
                    throw new IllegalArgumentException("gravity must be set to either top or bottom");
                }
                mIsSlidingUp = gravity == Gravity.BOTTOM;
                defAttrs.recycle();
            }

            TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.SlidingUpPanelLayout);
            if (ta != null) {
                mStyledAttributes = new StyledAttributes(ta);
                ta.recycle();
            }
        }

        final float density = context.getResources().getDisplayMetrics().density;
        mStyledAttributes.panelHeight = mStyledAttributes.panelHeight == -1 ? (int) (68 * density + 0.5f): mStyledAttributes.panelHeight;
        mStyledAttributes.shadowHeight = mStyledAttributes.shadowHeight == -1? (int) (4 * density + 0.5f):mStyledAttributes.shadowHeight;
        mStyledAttributes.parallaxOffset = mStyledAttributes.parallaxOffset == -1?0:mStyledAttributes.parallaxOffset;
        // If the shadow height is zero, don't show the shadow
        mShadowDrawable = mStyledAttributes.shadowHeight > 0? ContextCompat.getDrawable(context, mIsSlidingUp? R.drawable.above_shadow:R.drawable.below_shadow):null;
        setWillNotDraw(false);
        mDragHelper = ViewDragHelper.create(this, 0.5f, new DragHelperCallback());
        mDragHelper.setMinVelocity(density);
    }

    /**
     * Set the Drag View after the view is inflated
     */
    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        if (mStyledAttributes.dragViewResId != -1) {
            setDragView(findViewById(mStyledAttributes.dragViewResId));
        }
    }

    /**
     * Set the draggable view portion. Use to null, to allow the whole panel to be draggable
     *
     * @param dragView A view that will be used to drag the panel.
     */
    public void setDragView(View dragView) {
        if (mDragView != null && mStyledAttributes.dragViewClickable) {
            mDragView.setOnClickListener(null);
        }
        mDragView = dragView;
        if (mDragView != null) {
            mDragView.setClickable(true);
            mDragView.setFocusable(false);
            mDragView.setFocusableInTouchMode(false);
            if (mStyledAttributes.dragViewClickable) {
                mDragView.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (!isEnabled()) return;
                        if (!isPanelExpanded() && !isPanelAnchored()) {
                            mSlideableView.setVisibility(View.VISIBLE);
                        } else {
                            collapsePanel();
                        }
                    }
                });
            }
        }
    }

    void updateObscuredViewVisibility() {
        if (getChildCount() == 0) {
            return;
        }
        final int leftBound = getPaddingLeft();
        final int rightBound = getWidth() - getPaddingRight();
        final int topBound = getPaddingTop();
        final int bottomBound = getHeight() - getPaddingBottom();
        final int left;
        final int right;
        final int top;
        final int bottom;
        if (mSlideableView != null && (mSlideableView.getBackground() != null && mSlideableView.getBackground().getOpacity() == PixelFormat.OPAQUE)) {
            left = mSlideableView.getLeft();
            right = mSlideableView.getRight();
            top = mSlideableView.getTop();
            bottom = mSlideableView.getBottom();
        } else {
            left = right = top = bottom = 0;
        }
        View child = mMainView;
        final int clampedChildLeft = Math.max(leftBound, child.getLeft());
        final int clampedChildTop = Math.max(topBound, child.getTop());
        final int clampedChildRight = Math.min(rightBound, child.getRight());
        final int clampedChildBottom = Math.min(bottomBound, child.getBottom());
        final int vis;
        if (clampedChildLeft >= left && clampedChildTop >= top &&
                clampedChildRight <= right && clampedChildBottom <= bottom) {
            vis = INVISIBLE;
        } else {
            vis = VISIBLE;
        }
        child.setVisibility(vis);
    }

    void setAllChildrenVisible() {
        for (int i = 0, childCount = getChildCount(); i < childCount; i++) {
            final View child = getChildAt(i);
            if (child.getVisibility() == INVISIBLE) {
                child.setVisibility(VISIBLE);
            }
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mFirstLayout = true;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mFirstLayout = true;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.EXACTLY) {
            throw new IllegalStateException("Width must have an exact value or MATCH_PARENT");
        } else if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.EXACTLY) {
            throw new IllegalStateException("Height must have an exact value or MATCH_PARENT");
        }

        final int childCount = getChildCount();

        if (childCount != 2 && childCount != 3) {
            throw new IllegalStateException("Sliding up panel layout must have exactly 2 or 3 children!");
        }

        if (childCount == 2) {
            mMainView = getChildAt(0);
            mSlideableView = getChildAt(1);
        } else {
            mMainView = getChildAt(1);
            mSlideableView = getChildAt(2);
        }

        if (mDragView == null) {
            setDragView(mSlideableView);
        }

        // If the sliding panel is not visible, then put the whole view in the hidden state
        if (mSlideableView.getVisibility() == GONE) {
            mSlideState = SlideState.HIDDEN;
        }

        int layoutHeight = MeasureSpec.getSize(heightMeasureSpec) - getPaddingTop() - getPaddingBottom();

        // First pass. Measure based on child LayoutParams width/height.
        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);
            final LayoutParams lp = (LayoutParams) child.getLayoutParams();

            // We always measure the sliding panel in order to know it's height (needed for show panel)
            if (child.getVisibility() == GONE && child == mMainView) {
                continue;
            }

            int height = layoutHeight;
            if (child == mMainView && !mStyledAttributes.overlayContent && mSlideState != SlideState.HIDDEN) {
                height -= mStyledAttributes.panelHeight;
            }

            int childWidthSpec;
            if (lp.width == LayoutParams.WRAP_CONTENT) {
                childWidthSpec = MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.AT_MOST);
            } else if (lp.width == LayoutParams.MATCH_PARENT) {
                childWidthSpec = MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY);
            } else {
                childWidthSpec = MeasureSpec.makeMeasureSpec(lp.width, MeasureSpec.EXACTLY);
            }

            int childHeightSpec;
            if (lp.height == LayoutParams.WRAP_CONTENT) {
                childHeightSpec = MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.AT_MOST);
            } else if (lp.height == LayoutParams.MATCH_PARENT) {
                childHeightSpec = MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY);
            } else {
                childHeightSpec = MeasureSpec.makeMeasureSpec(lp.height, MeasureSpec.EXACTLY);
            }

            if (child == mSlideableView) {
                mSlideRange = MeasureSpec.getSize(childHeightSpec) - mStyledAttributes.panelHeight + mStyledAttributes.slidePanelOffset;
                childHeightSpec += mStyledAttributes.slidePanelOffset;
            }

            child.measure(childWidthSpec, childHeightSpec);
        }

        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        final int paddingLeft = getPaddingLeft();
        final int paddingTop = getPaddingTop();

        final int childCount = getChildCount();

        if (mFirstLayout) {
            switch (mSlideState) {
                case EXPANDED:
                    mSlideOffset = 1.0f;
                    break;
                case ANCHORED:
                    mSlideOffset = mStyledAttributes.anchorPoint;
                    break;
                case HIDDEN:
                    int newTop = computePanelTopPosition(0.0f) + (mIsSlidingUp ? + mStyledAttributes.panelHeight : -mStyledAttributes.panelHeight);
                    mSlideOffset = computeSlideOffset(newTop);
                    break;
                default:
                    mSlideOffset = 0.f;
                    break;
            }
        }

        for (int i = 0; i < childCount; i++) {
            final View child = getChildAt(i);

            // Always layout the sliding view on the first layout
            if (child.getVisibility() == GONE && (child == mMainView || mFirstLayout)) {
                continue;
            }

            final int childHeight = child.getMeasuredHeight();
            int childTop = paddingTop;

            if (child == mSlideableView) {
                childTop = computePanelTopPosition(mSlideOffset);
            }

            if (!mIsSlidingUp) {
                if (child == mMainView && !mStyledAttributes.overlayContent) {
                    childTop = computePanelTopPosition(mSlideOffset) + mSlideableView.getMeasuredHeight();
                }
            }
            final int childBottom = childTop + childHeight;
            final int childLeft = paddingLeft;
            final int childRight = childLeft + child.getMeasuredWidth();

            child.layout(childLeft, childTop, childRight, childBottom);
        }

        if (mFirstLayout) {
            updateObscuredViewVisibility();
        }

        mFirstLayout = false;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        // Recalculate sliding panes and their details
        if (h != oldh) {
            mFirstLayout = true;
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        if (!enabled) {
            collapsePanel();
        }
        super.setEnabled(enabled);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        final int action = MotionEventCompat.getActionMasked(ev);


        if (!isEnabled() || (mIsUnableToDrag && action != MotionEvent.ACTION_DOWN)) {
            mDragHelper.cancel();
            return super.onInterceptTouchEvent(ev);
        }

        if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
            mDragHelper.cancel();
            return false;
        }

        final float x = ev.getX();
        final float y = ev.getY();

        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                mIsUnableToDrag = false;
                mInitialMotionX = x;
                mInitialMotionY = y;
                break;
            }

            case MotionEvent.ACTION_MOVE: {
                final float adx = Math.abs(x - mInitialMotionX);
                final float ady = Math.abs(y - mInitialMotionY);
                final int dragSlop = mDragHelper.getTouchSlop();

                // Handle any horizontal scrolling on the drag view.
                if (adx > dragSlop && ady < dragSlop) {
                    return super.onInterceptTouchEvent(ev);
                }


                boolean isDragViewUnder = false;
                if (mDragView != null) {
                    final int[] viewLocation = new int[2];
                    mDragView.getLocationOnScreen(viewLocation);
                    final int[] parentLocation = new int[2];
                    this.getLocationOnScreen(parentLocation);
                    final int screenX = parentLocation[0] + (int) mInitialMotionX;
                    final int screenY = parentLocation[1] + (int) mInitialMotionY;
                    isDragViewUnder = screenX >= viewLocation[0] && screenX < viewLocation[0] + mDragView.getWidth() &&
                            screenY >= viewLocation[1] && screenY < viewLocation[1] + mDragView.getHeight();
                }

                if ((ady > dragSlop && adx > ady) || !isDragViewUnder) {
                    mDragHelper.cancel();
                    mIsUnableToDrag = true;
                    return false;
                }
                break;
            }
        }

        return mDragHelper.shouldInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (mSlideableView == null) {
            return super.onTouchEvent(ev);
        }
        mDragHelper.processTouchEvent(ev);
        return true;
    }

    /*
     * Computes the top position of the panel based on the slide offset.
     */
    private int computePanelTopPosition(float slideOffset) {
        int slidingViewHeight = mSlideableView != null ? mSlideableView.getMeasuredHeight() : 0;
        int slidePixelOffset = (int) (slideOffset * mSlideRange);
        // Compute the top of the panel if its collapsed
        return mIsSlidingUp
                ? getMeasuredHeight() - getPaddingBottom() - mStyledAttributes.panelHeight - slidePixelOffset
                : getPaddingTop() - slidingViewHeight + mStyledAttributes.panelHeight + slidePixelOffset;
    }

    /*
     * Computes the slide offset based on the top position of the panel
     */
    private float computeSlideOffset(int topPosition) {
        // Compute the panel top position if the panel is collapsed (offset 0)
        final int topBoundCollapsed = computePanelTopPosition(0);

        // Determine the new slide offset based on the collapsed top position and the new required
        // top position
        return (mIsSlidingUp
                ? (float) (topBoundCollapsed - topPosition) / mSlideRange
                : (float) (topPosition - topBoundCollapsed) / mSlideRange);
    }

    /**
     * Collapse the sliding pane if it is currently slideable. If first layout
     * has already completed this will animate.
     *
     * @return true if the pane was slideable and is now collapsed/in the process of collapsing
     */
    public boolean collapsePanel() {
        if (mFirstLayout) {
            mSlideState = SlideState.COLLAPSED;
            return true;
        } else {
            if (mSlideState == SlideState.HIDDEN || mSlideState == SlideState.COLLAPSED)
                return false;
            if (mSlideableView == null || mSlideState == SlideState.EXPANDED) return false;
            mSlideableView.setVisibility(View.VISIBLE);
            return mFirstLayout || smoothSlideTo(0.0f, 0);
        }
    }


    /**
     * Check if the sliding panel in this layout is fully expanded.
     *
     * @return true if sliding panel is completely expanded
     */
    public boolean isPanelExpanded() {
        return mSlideState == SlideState.EXPANDED;
    }

    /**
     * Check if the sliding panel in this layout is anchored.
     *
     * @return true if sliding panel is anchored
     */
    public boolean isPanelAnchored() {
        return mSlideState == SlideState.ANCHORED;
    }

    /**
     * Check if the sliding panel in this layout is currently visible.
     *
     * @return true if the sliding panel is visible.
     */
    public boolean isPanelHidden() {
        return mSlideState == SlideState.HIDDEN;
    }

    /**
     * Shows the panel from the hidden state
     */
    public void showPanel() {
        if (mFirstLayout) {
            mSlideState = SlideState.COLLAPSED;
        } else {
            if (mSlideableView == null || mSlideState != SlideState.HIDDEN) return;
            mSlideableView.setVisibility(View.VISIBLE);
            requestLayout();
            smoothSlideTo(0, 0);
        }
    }

    /**
     * Hides the sliding panel entirely.
     */
    public void hidePanel() {
        if (mFirstLayout) {
            mSlideState = SlideState.HIDDEN;
        } else {
            if (mSlideState == SlideState.DRAGGING || mSlideState == SlideState.HIDDEN) return;
            int newTop = computePanelTopPosition(0.0f) + (mIsSlidingUp ? + mStyledAttributes.panelHeight : -mStyledAttributes.panelHeight);
            smoothSlideTo(computeSlideOffset(newTop), 0);
        }
    }

    @SuppressLint("NewApi")
    private void onPanelDragged(int newTop) {
        mSlideState = SlideState.DRAGGING;
        // Recompute the slide offset based on the new top position
        mSlideOffset = computeSlideOffset(newTop);
        // Update the parallax based on the new slide offset
        if ((mStyledAttributes.parallaxOffset > 0 || mStyledAttributes.directOffset) && mSlideOffset >= 0) {
            int mainViewOffset = 0;
            if (mStyledAttributes.parallaxOffset > 0) {
                mainViewOffset = mStyledAttributes.parallaxOffset < 0 ? 0: (int) (mStyledAttributes.parallaxOffset * (mIsSlidingUp ? -mSlideOffset : mSlideOffset));
            } else {
                mainViewOffset = (int) ((mIsSlidingUp ? -mSlideOffset : mSlideOffset) * mSlideRange);
            }

            mMainView.setTranslationY(mainViewOffset);
        }

        // Dispatch the slide event
        if (panelSlideListener != null) {
            panelSlideListener.onPanelSlide(mSlideableView, mSlideOffset);
        }
        // If the slide offset is negative, and overlay is not on, we need to increase the
        // height of the main content
        if (mSlideOffset <= 0 && !mStyledAttributes.overlayContent) {
            // expand the main view
            LayoutParams lp = (LayoutParams) mMainView.getLayoutParams();
            lp.height = mIsSlidingUp ? (newTop - getPaddingBottom()) : (getHeight() - getPaddingBottom() - mSlideableView.getMeasuredHeight() - newTop);
            mMainView.requestLayout();
        }
    }

    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        boolean result;
        final int save = canvas.save();
        final Rect tmpRect = new Rect();

        if (mSlideableView != null && mMainView == child) {
            // Clip against the slider; no sense drawing what will immediately be covered,
            // Unless the panel is set to overlay content
            if (!mStyledAttributes.overlayContent) {
                canvas.getClipBounds(tmpRect);
                if (mIsSlidingUp) {
                    tmpRect.bottom = Math.min(tmpRect.bottom, mSlideableView.getTop());
                } else {
                    tmpRect.top = Math.max(tmpRect.top, mSlideableView.getBottom());
                }
                canvas.clipRect(tmpRect);
            }
        }

        result = super.drawChild(canvas, child, drawingTime);
        canvas.restoreToCount(save);

        if (mStyledAttributes.coveredFadeColor != 0 && mSlideOffset > 0) {
            final int baseAlpha = (mStyledAttributes.coveredFadeColor & 0xff000000) >>> 24;
            final int imag = (int) (baseAlpha * mSlideOffset);
            final int color = imag << 24 | (mStyledAttributes.coveredFadeColor & 0xffffff);
            final Paint coveredFadePaint = new Paint();
            coveredFadePaint.setColor(color);
            canvas.drawRect(tmpRect, coveredFadePaint);
        }

        return result;
    }

    /**
     * Smoothly animate mDraggingPane to the target X position within its range.
     *
     * @param slideOffset position to animate to
     * @param velocity    initial velocity in case of fling, or 0.
     */
    boolean smoothSlideTo(float slideOffset, int velocity) {
        if (mSlideableView == null) {
            // Nothing to do.
            return false;
        }

        int panelTop = computePanelTopPosition(slideOffset);
        if (mDragHelper.smoothSlideViewTo(mSlideableView, mSlideableView.getLeft(), panelTop)) {
            setAllChildrenVisible();
            ViewCompat.postInvalidateOnAnimation(this);
            return true;
        }
        return false;
    }

    @Override
    public void computeScroll() {
        if (mDragHelper != null && mDragHelper.continueSettling(true)) {
            if (mSlideableView == null) {
                mDragHelper.abort();
                return;
            }

            ViewCompat.postInvalidateOnAnimation(this);
        }
    }

    @Override
    public void draw(Canvas c) {
        super.draw(c);

        if (mSlideableView == null) {
            // No need to draw a shadow if we don't have one.
            return;
        }

        final int right = mSlideableView.getRight();
        final int top;
        final int bottom;
        if (mIsSlidingUp) {
            top = mSlideableView.getTop() - mStyledAttributes.shadowHeight;
            bottom = mSlideableView.getTop();
        } else {
            top = mSlideableView.getBottom();
            bottom = mSlideableView.getBottom() + mStyledAttributes.shadowHeight;
        }
        final int left = mSlideableView.getLeft();

        if (mShadowDrawable != null) {
            mShadowDrawable.setBounds(left, top, right, bottom);
            mShadowDrawable.draw(c);
        }
    }

    @Override
    protected ViewGroup.LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams();
    }

    @Override
    protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof MarginLayoutParams
                ? new LayoutParams((MarginLayoutParams) p)
                : new LayoutParams(p);
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof LayoutParams && super.checkLayoutParams(p);
    }

    @Override
    public ViewGroup.LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new LayoutParams(getContext(), attrs);
    }

    @Override
    public Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();

        SavedState ss = new SavedState(superState);
        ss.mSlideState = mSlideState;

        return ss;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());
        mSlideState = ss.mSlideState;
    }

    /**
     * Current state of the slideable view.
     */
    private enum SlideState {
        EXPANDED,
        COLLAPSED,
        ANCHORED,
        HIDDEN,
        DRAGGING
    }

    /**
     * Listener for monitoring events about sliding panes.
     */
    public interface PanelSlideListener {
        /**
         * Called when a sliding pane's position changes.
         *
         * @param panel       The child view that was moved
         * @param slideOffset The new offset of this sliding pane within its range, from 0-1
         */
        void onPanelSlide(View panel, float slideOffset);

        /**
         * Called when a sliding panel becomes slid completely collapsed.
         *
         * @param panel The child view that was slid to an collapsed position
         */
        void onPanelCollapsed(View panel);

        /**
         * Called when a sliding panel becomes slid completely expanded.
         *
         * @param panel The child view that was slid to a expanded position
         */
        void onPanelExpanded(View panel);

        /**
         * Called when a sliding panel becomes anchored.
         *
         * @param panel The child view that was slid to a anchored position
         */
        void onPanelAnchored(View panel);

        /**
         * Called when a sliding panel becomes completely hidden.
         *
         * @param panel The child view that was slid to a hidden position
         */
        void onPanelHidden(View panel);
    }

    public static class LayoutParams extends MarginLayoutParams {
        private static final int[] ATTRS = new int[]{
                android.R.attr.layout_weight
        };

        public LayoutParams() {
            super(MATCH_PARENT, MATCH_PARENT);
        }

        public LayoutParams(int width, int height) {
            super(width, height);
        }

        public LayoutParams(ViewGroup.LayoutParams source) {
            super(source);
        }

        public LayoutParams(MarginLayoutParams source) {
            super(source);
        }

        public LayoutParams(LayoutParams source) {
            super(source);
        }

        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);

            final TypedArray a = c.obtainStyledAttributes(attrs, ATTRS);
            a.recycle();
        }

    }

    static class SavedState extends BaseSavedState {
        public static final Creator<SavedState> CREATOR =
                new Creator<SavedState>() {
                    @Override
                    public SavedState createFromParcel(Parcel in) {
                        return new SavedState(in);
                    }

                    @Override
                    public SavedState[] newArray(int size) {
                        return new SavedState[size];
                    }
                };
        SlideState mSlideState;

        SavedState(Parcelable superState) {
            super(superState);
        }

        private SavedState(Parcel in) {
            super(in);
            try {
                mSlideState = Enum.valueOf(SlideState.class, in.readString());
            } catch (IllegalArgumentException e) {
                mSlideState = SlideState.COLLAPSED;
            }
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeString(mSlideState.toString());
        }
    }

    private class DragHelperCallback extends ViewDragHelper.Callback {

        @Override
        public boolean tryCaptureView(View child, int pointerId) {
            if (mIsUnableToDrag) {
                return false;
            }

            return child == mSlideableView;
        }

        @Override
        public void onViewDragStateChanged(int state) {
            if (mDragHelper.getViewDragState() == ViewDragHelper.STATE_IDLE) {
                mSlideOffset = computeSlideOffset(mSlideableView.getTop());

                if (mSlideOffset == 1) {
                    if (mSlideState != SlideState.EXPANDED) {
                        updateObscuredViewVisibility();
                        mSlideState = SlideState.EXPANDED;
                        if (panelSlideListener != null) {
                            panelSlideListener.onPanelExpanded(mSlideableView);
                        }
                        sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
                    }
                } else if (mSlideOffset == 0) {
                    if (mSlideState != SlideState.COLLAPSED) {
                        mSlideState = SlideState.COLLAPSED;
                        if (panelSlideListener != null) {
                            panelSlideListener.onPanelCollapsed(mSlideableView);
                        }
                        sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
                    }
                } else if (mSlideOffset < 0) {
                    mSlideState = SlideState.HIDDEN;
                    mSlideableView.setVisibility(View.GONE);
                    if (panelSlideListener != null) {
                        panelSlideListener.onPanelHidden(mSlideableView);
                    }
                    sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
                } else if (mSlideState != SlideState.ANCHORED) {
                    updateObscuredViewVisibility();
                    mSlideState = SlideState.ANCHORED;
                    if (panelSlideListener != null) {
                        panelSlideListener.onPanelAnchored(mSlideableView);
                    }
                    sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
                }
            }
        }

        @Override
        public void onViewCaptured(View capturedChild, int activePointerId) {
            setAllChildrenVisible();
        }

        @Override
        public void onViewPositionChanged(View changedView, int left, int top, int dx, int dy) {
            onPanelDragged(top);
            invalidate();
        }

        @Override
        public void onViewReleased(View releasedChild, float xvel, float yvel) {
            int target = 0;

            // direction is always positive if we are sliding in the expanded direction
            float direction = mIsSlidingUp ? -yvel : yvel;

            if (direction > 0) {
                // swipe up -> expand
                target = computePanelTopPosition(1.0f);
            } else if (direction < 0) {
                // swipe down -> collapse
                target = computePanelTopPosition(0.0f);
            } else if (mStyledAttributes.anchorPoint != 1 && mSlideOffset >= (1.f + mStyledAttributes.anchorPoint) / 2) {
                // zero velocity, and far enough from anchor point => expand to the top
                target = computePanelTopPosition(1.0f);
            } else if (mStyledAttributes.anchorPoint == 1 && mSlideOffset >= 0.5f) {
                // zero velocity, and far enough from anchor point => expand to the top
                target = computePanelTopPosition(1.0f);
            } else if (mStyledAttributes.anchorPoint != 1 && mSlideOffset >= mStyledAttributes.anchorPoint) {
                target = computePanelTopPosition(mStyledAttributes.anchorPoint);
            } else if (mStyledAttributes.anchorPoint != 1 && mSlideOffset >= mStyledAttributes.anchorPoint / 2) {
                target = computePanelTopPosition(mStyledAttributes.anchorPoint);
            } else {
                // settle at the bottom
                target = computePanelTopPosition(0.0f);
            }

            mDragHelper.settleCapturedViewAt(releasedChild.getLeft(), target);
            invalidate();
        }

        @Override
        public int getViewVerticalDragRange(View child) {
            return mSlideRange;
        }

        @Override
        public int clampViewPositionVertical(View child, int top, int dy) {
            final int collapsedTop = computePanelTopPosition(0.f);
            final int expandedTop = computePanelTopPosition(1.0f);
            if (mIsSlidingUp) {
                return Math.min(Math.max(top, expandedTop), collapsedTop);
            } else {
                return Math.min(Math.max(top, collapsedTop), expandedTop);
            }
        }
    }
}
