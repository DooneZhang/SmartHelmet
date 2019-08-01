package com.datang.smarthelmet.settings.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.annotation.Nullable;
import com.datang.smarthelmet.R;

public class SeekBarSetting extends BasicSetting {
    private SeekBar mSeekBar;
    private TextView description;

    public SeekBarSetting(Context context) {
        super(context);
    }

    public SeekBarSetting(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public SeekBarSetting(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public SeekBarSetting(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    protected void inflateView() {
        mView =
                LayoutInflater.from(getContext())
                        .inflate(R.layout.settings_widget_seekbar, this, true);
    }

    protected void init(@Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super.init(attrs, defStyleAttr, defStyleRes);

        mSeekBar = mView.findViewById(R.id.setting_seekBar);
        description = findViewById(R.id.setting_seekBar_description);

        mSeekBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    /** 拖动条进度改变的时候调用 */
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        if (mListener != null) {
                            mListener.onBoolValueChanged(fromUser);
                            description.setText("当前进度：" + progress + "%");
                        }
                    }
                    /** 拖动条开始拖动的时候调用 */
                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                        if (mListener != null) {
                            description.setText("开始拖动");
                        }
                    }
                    /** 拖动条停止拖动的时候调用 */
                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                        if (mListener != null) {
                            description.setText("拖动停止");
                        }
                    }
                });

        RelativeLayout rlayout = mView.findViewById(R.id.setting_layout);

        rlayout.setOnClickListener(
                new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (mSeekBar.isEnabled()) {}
                    }
                });
    }

    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        mSeekBar.setEnabled(enabled);
    }
}
