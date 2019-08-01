package com.datang.smarthelmet.recording;

/*
RecordingsActivity.java
Copyright (C) 2019 Belledonne Communications, Grenoble, France

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
*/

import android.Manifest;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.datang.smarthelmet.R;
import com.datang.smarthelmet.SmartHelmetManager;
import com.datang.smarthelmet.activities.MainActivity;
import com.datang.smarthelmet.utils.FileUtils;
import com.datang.smarthelmet.utils.SelectableHelper;
import com.datang.smarthelmet.views.SmartHelmetLinearLayoutManager;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RecordingsActivity extends MainActivity
        implements SelectableHelper.DeleteListener, RecordingViewHolder.ClickListener {
    private RecyclerView mRecordingList;
    private List<Recording> mRecordings;
    private TextView mNoRecordings;
    private RecordingsAdapter mRecordingsAdapter;
    private SelectableHelper mSelectableHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (mAbortCreation) {
            return;
        }

        mOnBackPressGoHome = false;
        mAlwaysHideTabBar = true;

        // Uses the fragment container layout to inflate the about view instead of using a fragment
        View recordingsView =
                LayoutInflater.from(this).inflate(R.layout.recordings_list, null, false);
        LinearLayout fragmentContainer = findViewById(R.id.fragmentContainer);
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        fragmentContainer.addView(recordingsView, params);

        if (isTablet()) {
            findViewById(R.id.fragmentContainer2).setVisibility(View.GONE);
        }

        ImageView backButton = findViewById(R.id.back);
        backButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        goBack();
                    }
                });

        mSelectableHelper = new SelectableHelper(findViewById(R.id.root_layout), this);

        mRecordingList = findViewById(R.id.recording_list);
        mNoRecordings = findViewById(R.id.no_recordings);

        LinearLayoutManager mLayoutManager = new SmartHelmetLinearLayoutManager(this);
        mRecordingList.setLayoutManager(mLayoutManager);

        // Divider between items
        DividerItemDecoration dividerItemDecoration =
                new DividerItemDecoration(this, mLayoutManager.getOrientation());
        dividerItemDecoration.setDrawable(getResources().getDrawable(R.drawable.divider));
        mRecordingList.addItemDecoration(dividerItemDecoration);

        mRecordings = new ArrayList<>();

        mPermissionsToHave =
                new String[] {
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                };
    }

    @Override
    protected void onResume() {
        super.onResume();

        hideTopBar();
        hideTabBar();

        SmartHelmetManager.getAudioManager().setAudioManagerModeNormal();
        SmartHelmetManager.getAudioManager().routeAudioToSpeaker();

        removeDeletedRecordings();
        searchForRecordings();

        hideRecordingListAndDisplayMessageIfEmpty();
        mRecordingsAdapter = new RecordingsAdapter(this, mRecordings, this, mSelectableHelper);
        mRecordingList.setAdapter(mRecordingsAdapter);
        mSelectableHelper.setAdapter(mRecordingsAdapter);
        mSelectableHelper.setDialogMessage(R.string.recordings_delete_dialog);
    }

    @Override
    protected void onPause() {
        super.onPause();

        SmartHelmetManager.getAudioManager().routeAudioToEarPiece();

        // Close all opened mRecordings
        for (Recording r : mRecordings) {
            if (!r.isClosed()) {
                if (r.isPlaying()) r.pause();
                r.close();
            }
        }
    }

    @Override
    protected void onDestroy() {
        mRecordingList = null;
        mRecordings = null;
        mNoRecordings = null;
        mRecordingsAdapter = null;
        mSelectableHelper = null;

        super.onDestroy();
    }

    @Override
    public void onDeleteSelection(Object[] objectsToDelete) {
        int size = mRecordingsAdapter.getSelectedItemCount();
        for (int i = 0; i < size; i++) {
            Recording record = (Recording) objectsToDelete[i];

            if (record.isPlaying()) record.pause();
            record.close();

            File recordingFile = new File(record.getRecordPath());
            if (recordingFile.delete()) {
                mRecordings.remove(record);
            }
        }
        hideRecordingListAndDisplayMessageIfEmpty();
    }

    @Override
    public void onItemClicked(int position) {
        if (mRecordingsAdapter.isEditionEnabled()) {
            mRecordingsAdapter.toggleSelection(position);
        }
    }

    @Override
    public boolean onItemLongClicked(int position) {
        if (!mRecordingsAdapter.isEditionEnabled()) {
            mSelectableHelper.enterEditionMode();
        }
        mRecordingsAdapter.toggleSelection(position);
        return true;
    }

    private void hideRecordingListAndDisplayMessageIfEmpty() {
        if (mRecordings == null || mRecordings.isEmpty()) {
            mNoRecordings.setVisibility(View.VISIBLE);
            mRecordingList.setVisibility(View.GONE);
        } else {
            mNoRecordings.setVisibility(View.GONE);
            mRecordingList.setVisibility(View.VISIBLE);
        }
    }

    private void removeDeletedRecordings() {
        String recordingsDirectory = FileUtils.getRecordingsDirectory(this);
        File directory = new File(recordingsDirectory);

        if (directory.exists() && directory.isDirectory()) {
            File[] existingRecordings = directory.listFiles();

            for (Recording r : mRecordings) {
                boolean exists = false;
                for (File f : existingRecordings) {
                    if (f.getPath().equals(r.getRecordPath())) {
                        exists = true;
                        break;
                    }
                }

                if (!exists) mRecordings.remove(r);
            }

            Collections.sort(mRecordings);
        }
        hideRecordingListAndDisplayMessageIfEmpty();
    }

    private void searchForRecordings() {
        String recordingsDirectory = FileUtils.getRecordingsDirectory(this);
        File directory = new File(recordingsDirectory);

        if (directory.exists() && directory.isDirectory()) {
            File[] existingRecordings = directory.listFiles();
            if (existingRecordings == null) return;

            for (File f : existingRecordings) {
                boolean exists = false;
                for (Recording r : mRecordings) {
                    if (r.getRecordPath().equals(f.getPath())) {
                        exists = true;
                        break;
                    }
                }

                if (!exists) {
                    if (Recording.RECORD_PATTERN.matcher(f.getPath()).matches()) {
                        mRecordings.add(new Recording(this, f.getPath()));
                    }
                }
            }

            Collections.sort(mRecordings);
        }
    }
}
