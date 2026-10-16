package com.vixmobi.auto.ui.notifications;


import static com.vixmobi.auto.Constant.XIAOPANG_INFO;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class NotificationsViewModel extends ViewModel {

    private final MutableLiveData<String> mText;

    public NotificationsViewModel() {
        mText = new MutableLiveData<>();
        mText.setValue(XIAOPANG_INFO);
    }

    public LiveData<String> getText() {
        return mText;
    }
}