/*
 * Copyright (C) 2011 The Android Open Source Project
 * Copyright (c) 2011-12, The Linux Foundation. All rights reserved.
 * Not a Contribution, Apache license notifications and license are retained
 * for attribution purposes only
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.contacts.dialpad;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.ListFragment;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.CharArrayBuffer;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.net.Uri.Builder;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.provider.CallLog.Calls;
import android.provider.Contacts.Intents.Insert;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.provider.Contacts.People;
import android.provider.Contacts.Phones;
import android.provider.Contacts.PhonesColumns;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.telephony.MSimTelephonyManager;
import android.telephony.PhoneNumberUtils;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.DialerKeyListener;
import android.text.style.BackgroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CursorAdapter;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.QuickContactBadge;
import android.widget.SectionIndexer;
import android.widget.TextView;

import com.qrd.plugin.feature_query.FeatureQuery;

import com.android.contacts.ContactPhotoManager;
import com.android.contacts.ContactsUtils;
import com.android.contacts.R;
import com.android.contacts.SpecialCharSequenceMgr;
import com.android.contacts.activities.DialtactsActivity;
import com.android.contacts.list.ContactListItemView;
import com.android.contacts.list.ContactListItemView.PhotoPosition;
import com.android.contacts.util.Constants;
import com.android.contacts.util.PhoneNumberFormatter;
import com.android.contacts.util.StopWatch;
import com.android.contacts.widget.TextWithHighlighting;
import com.android.internal.telephony.ITelephony;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import com.android.internal.telephony.msim.ITelephonyMSim;
import com.android.phone.CallLogAsync;
import com.android.phone.HapticFeedback;

import com.android.internal.widget.multiwaveview.GlowPadView;
import static com.android.internal.telephony.MSimConstants.SUBSCRIPTION_KEY;
import com.android.internal.telephony.MSimConstants;

import com.android.contacts.DialpadCling;
import com.android.contacts.ContactsLib;

/**
 * Fragment that displays a twelve-key phone dialpad.
 */
public class DialpadFragment extends ListFragment
        implements View.OnClickListener,
        View.OnLongClickListener, View.OnKeyListener,
        AdapterView.OnItemClickListener, TextWatcher,
        PopupMenu.OnMenuItemClickListener,
        DialpadImageButton.OnPressedListener {
    private static final String TAG = DialpadFragment.class.getSimpleName();

    private static final boolean DEBUG = DialtactsActivity.DEBUG;

    private static final String SUBSCRIPTION_KEY = "subscription";
    private static final String EMPTY_NUMBER = "";

    /** The length of DTMF tones in milliseconds */
    private static final int TONE_LENGTH_MS = 150;
    private static final int TONE_LENGTH_INFINITE = -1;

    /** The DTMF tone volume relative to other sounds in the stream */
    private static final int TONE_RELATIVE_VOLUME = 80;

    /** Stream type used to play the DTMF tones off call, and mapped to the volume control keys */
    private static final int DIAL_TONE_STREAM_TYPE = AudioManager.STREAM_DTMF;

    /**
     * View (usually FrameLayout) containing mDigits field. This can be null, in which mDigits
     * isn't enclosed by the container.
     */
    private View mDigitsContainer;
    private EditText mDigits;

    /** Remembers if we need to clear digits field when the screen is completely gone. */
    private boolean mClearDigitsOnStop;

    private View mDelete;
    private ToneGenerator mToneGenerator;
    private final Object mToneGeneratorLock = new Object();
    private View mDialpad;
    /**
     * Remembers the number of dialpad buttons which are pressed at this moment.
     * If it becomes 0, meaning no buttons are pressed, we'll call
     * {@link ToneGenerator#stopTone()}; the method shouldn't be called unless the last key is
     * released.
     */
    private int mDialpadPressCount;

    private View mDialButtonContainer;
    private View mDialButton;
    private GlowPadView mDialWidget;
    public GlowPadViewMethods glowPadViewMethods;
    private ListView mDialpadChooser;
    private DialpadChooserAdapter mDialpadChooserAdapter;

    private DialpadCling mDialpadCling;
    private TextView mClingText;
    private int transferX = 0;
    private int transferY = 0;
    private boolean mDialButtonLongClicked = false;

    /**
     * Regular expression prohibiting manual phone call. Can be empty, which means "no rule".
     */
    private String mProhibitedPhoneNumberRegexp;

    private int mSubscription = 0;

    // Last number dialed, retrieved asynchronously from the call DB
    // in onCreate. This number is displayed when the user hits the
    // send key and cleared in onPause.
    private final CallLogAsync mCallLog = new CallLogAsync();
    private String mLastNumberDialed = EMPTY_NUMBER;

    // determines if we want to playback local DTMF tones.
    private boolean mDTMFToneEnabled;

    // Vibration (haptic feedback) for dialer key presses.
    private final HapticFeedback mHaptic = new HapticFeedback();

    /** Identifier for the "Add Call" intent extra. */
    private static final String ADD_CALL_MODE_KEY = "add_call_mode";

    // Cause display problems due to long telephone numbers, so the control of
    // the maximum length of the telephone number in 50 characters or less
    private static final int DIGITEXT_MAXSIZE = 50;

    /**
     * Identifier for intent extra for sending an empty Flash message for
     * CDMA networks. This message is used by the network to simulate a
     * press/depress of the "hookswitch" of a landline phone. Aka "empty flash".
     *
     * TODO: Using an intent extra to tell the phone to send this flash is a
     * temporary measure. To be replaced with an ITelephony call in the future.
     * TODO: Keep in sync with the string defined in OutgoingCallBroadcaster.java
     * in Phone app until this is replaced with the ITelephony API.
     */
    private static final String EXTRA_SEND_EMPTY_FLASH
            = "com.android.phone.extra.SEND_EMPTY_FLASH";

    private String mCurrentCountryIso;

    private static final int SUB0 = 0;
    private static final int SUB1 = 1;

    private String sub0Moderm = "";
    private String sub1Moderm = "";

    private PhoneStateListener getPhoneStateListener(final int subscription) {
        PhoneStateListener phoneStateListener = new PhoneStateListener(subscription) {
            /**
             * Listen for phone state changes so that we can take down the
             * "dialpad chooser" if the phone becomes idle while the
             * chooser UI is visible.
             */
            @Override
            public void onCallStateChanged(int state, String incomingNumber) {
                // Log.i(TAG, "PhoneStateListener.onCallStateChanged: "
                //       + state + ", '" + incomingNumber + "'");
                if (!phoneIsInUse() && dialpadChooserVisible()) {
                    // Log.i(TAG, "Call ended with dialpad chooser visible!  Taking it down...");
                    // Note there's a race condition in the UI here: the
                    // dialpad chooser could conceivably disappear (on its
                    // own) at the exact moment the user was trying to select
                    // one of the choices, which would be confusing.  (But at
                    // least that's better than leaving the dialpad chooser
                    // onscreen, but useless...)
                    showDialpadChooser(false);
                }
                // before display dialpad first refresh the hint string
                if (!phoneIsInUse()) {
                    // Common case; no hint necessary.
                    mDigits.setHint(null);
                }
            }
        };
        return phoneStateListener;
    }

    private boolean mWasEmptyBeforeTextChange;
    private boolean mDialButtonClickWithEmptyDigits;

    /**
     * This field is set to true while processing an incoming DIAL intent, in order to make sure
     * that SpecialCharSequenceMgr actions can be triggered by user input but *not* by a
     * tel: URI passed by some other app.  It will be set to false when all digits are cleared.
     */
    private boolean mDigitsFilledByIntent;

    private static final String PREF_DIGITS_FILLED_BY_INTENT = "pref_digits_filled_by_intent";

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        mWasEmptyBeforeTextChange = TextUtils.isEmpty(s);
    }

    @Override
    public void onTextChanged(CharSequence input, int start, int before, int changeCount) {
        if (mWasEmptyBeforeTextChange != TextUtils.isEmpty(input)) {
            final Activity activity = getActivity();
            if (activity != null) {
                activity.invalidateOptionsMenu();
            }
        } else if (mDialButtonClickWithEmptyDigits) {
            mDigits.setSelection(mDigits.getText().length());
            final Activity activity = getActivity();
            if (activity != null) {
                activity.invalidateOptionsMenu();
            }
            mDialButtonClickWithEmptyDigits = false;
        }

        // DTMF Tones do not need to be played here any longer -
        // the DTMF dialer handles that functionality now.
    }

    @Override
    public void afterTextChanged(Editable input) {
        // When DTMF dialpad buttons are being pressed, we delay SpecialCharSequencMgr sequence,
        // since some of SpecialCharSequenceMgr's behavior is too abrupt for the "touch-down"
        // behavior.
        if (!mDigitsFilledByIntent &&
                SpecialCharSequenceMgr.handleChars(getActivity(), input.toString(), mDigits)) {
            // A special sequence was entered, clear the digits
            mDigits.getText().clear();
        }

        if (isDigitsEmpty()) {
            mDigitsFilledByIntent = false;
            mDigits.setCursorVisible(false);
        }
        setQueryFilter();

        updateDialAndDeleteButtonEnabledState();
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        mCurrentCountryIso = ContactsUtils.getCurrentCountryIso(getActivity());

        try {
            mHaptic.init(getActivity(),
                         getResources().getBoolean(R.bool.config_enable_dialer_key_vibration));
        } catch (Resources.NotFoundException nfe) {
             Log.e(TAG, "Vibrate control bool missing.", nfe);
        }

        setHasOptionsMenu(true);

        mProhibitedPhoneNumberRegexp = getResources().getString(
                R.string.config_prohibited_phone_number_regexp);

        if (state != null) {
            mDigitsFilledByIntent = state.getBoolean(PREF_DIGITS_FILLED_BY_INTENT);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
        View fragmentView = inflater.inflate(R.layout.dialpad_fragment, container, false);

        // Load up the resources for the text field.
        Resources r = getResources();

        mDigitsContainer = fragmentView.findViewById(R.id.digits_container);
        //add for UX_smart dialer
        //mListlayout = fragmentView.findViewById(R.id.listlayout);
        mListoutside = fragmentView.findViewById(R.id.listoutside);
        mCountButton = fragmentView.findViewById(R.id.filterbutton);
        mCountButton.setOnClickListener(this);
        mCountView = (TextView)fragmentView.findViewById(R.id.filter_number);
        mCancel = (Button)fragmentView.findViewById(R.id.cancel_btn);
        mCancel.setOnClickListener(this);
        mAddContact = fragmentView.findViewById(R.id.add_contact);
        mAddContact.setOnClickListener(this);
        mAddContactText = (TextView)fragmentView.findViewById(R.id.add_contact_text);
        mDigits = (EditText) fragmentView.findViewById(R.id.digits);
        mDigits.setKeyListener(DialpadDialerKeyListener.getInstance());
        mDigits.setOnClickListener(this);
        mDigits.setOnKeyListener(this);
        mDigits.setOnLongClickListener(this);
        mDigits.addTextChangedListener(this);
        PhoneNumberFormatter.setPhoneNumberFormattingTextWatcher(getActivity(), mDigits);
        // Check for the presence of the keypad
        View oneButton = fragmentView.findViewById(R.id.one);
        if (oneButton != null) {
            setupKeypad(fragmentView);
        }

        DisplayMetrics dm = getResources().getDisplayMetrics();
        int minCellSize = (int) (56 * dm.density); // 56dip == minimum size of menu buttons
        int cellCount = dm.widthPixels / minCellSize;
        int fakeMenuItemWidth = dm.widthPixels / cellCount;
        mDialButtonContainer = fragmentView.findViewById(R.id.dialButtonContainer);
        // If in portrait, add padding to the dial button since we need space for the
        // search and menu/overflow buttons.
        if (mDialButtonContainer != null && !ContactsUtils.isLandscape(this.getActivity())) {
            mDialButtonContainer.setPadding(
                    fakeMenuItemWidth, mDialButtonContainer.getPaddingTop(),
                    fakeMenuItemWidth, mDialButtonContainer.getPaddingBottom());
        }
        mDialButton = fragmentView.findViewById(R.id.dialButton);
        if (r.getBoolean(R.bool.config_show_onscreen_dial_button)) {
            mDialButton.setOnClickListener(this);
            mDialButton.setOnLongClickListener(this);
            mDialButton.setOnTouchListener(((DialtactsActivity)getActivity()).getDialButtonTouchListener());
        } else {
            mDialButton.setVisibility(View.GONE); // It's VISIBLE by default
            mDialButton = null;
        }

        //add for UX_enhance_dial_button
        mDialWidget = (GlowPadView)fragmentView.findViewById(R.id.dialDualWidget);
        if (FeatureQuery.FEATURE_UX_DIALER_DIALBUTTON){
            if (mDialButton != null && mDialWidget != null) {
                glowPadViewMethods = new GlowPadViewMethods(mDialWidget);
                mDialWidget.setOnTriggerListener(glowPadViewMethods);
            }
        }else{
            mDialWidget.setVisibility(View.GONE);
            mDialWidget = null;
        }

        mDelete = fragmentView.findViewById(R.id.deleteButton);
        if (mDelete != null) {
            mDelete.setOnClickListener(this);
            mDelete.setOnLongClickListener(this);
        }

        mDialpad = fragmentView.findViewById(R.id.dialpad);  // This is null in landscape mode.

        // In landscape we put the keyboard in phone mode.
        if (null == mDialpad) {
            mDigits.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        } else {
            mDigits.setCursorVisible(false);
        }

        // Set up the "dialpad chooser" UI; see showDialpadChooser().
        mDialpadChooser = (ListView) fragmentView.findViewById(R.id.dialpadChooser);
        mDialpadChooser.setOnItemClickListener(this);

        //configureScreenFromIntent(getActivity().getIntent());

        mDialpadCling = (DialpadCling)fragmentView.findViewById(R.id.dialpad_cling);
        mClingText = (TextView)fragmentView.findViewById(R.id.clingText);
        final DialtactsActivity activity = (DialtactsActivity)getActivity();
        if (activity.canShowDialpadCling()){
            activity.showFirstRunDialpadCling();
        }
        return fragmentView;
    }

    private boolean isLayoutReady() {
        return mDigits != null;
    }

    public EditText getDigitsWidget() {
        return mDigits;
    }

    /**
     * @return true when {@link #mDigits} is actually filled by the Intent.
     */
    private boolean fillDigitsIfNecessary(Intent intent) {
        final String action = intent.getAction();
        if (Intent.ACTION_DIAL.equals(action) || Intent.ACTION_VIEW.equals(action)) {
            Uri uri = intent.getData();
            if (uri != null) {
                if (Constants.SCHEME_TEL.equals(uri.getScheme())) {
                    // Put the requested number into the input area
                    String data = uri.getSchemeSpecificPart();
                    // Remember it is filled via Intent.
                    mDigitsFilledByIntent = true;
                    final String converted = PhoneNumberUtils.convertKeypadLettersToDigits(
                            PhoneNumberUtils.replaceUnicodeDigits(data));
                    setFormattedDigits(converted, null);
                    return true;
                } else {
                    String type = intent.getType();
                    if (People.CONTENT_ITEM_TYPE.equals(type)
                            || Phones.CONTENT_ITEM_TYPE.equals(type)) {
                        // Query the phone number
                        Cursor c = getActivity().getContentResolver().query(intent.getData(),
                                new String[] {PhonesColumns.NUMBER, PhonesColumns.NUMBER_KEY},
                                null, null, null);
                        if (c != null) {
                            try {
                                if (c.moveToFirst()) {
                                    // Remember it is filled via Intent.
                                    mDigitsFilledByIntent = true;
                                    // Put the number into the input area
                                    setFormattedDigits(c.getString(0), c.getString(1));
                                    return true;
                                }
                            } finally {
                                c.close();
                            }
                        }
                    }
                }
            }
        }

        return false;
    }

    /**
     * @see #showDialpadChooser(boolean)
     */
    private static boolean needToShowDialpadChooser(Intent intent, boolean isAddCallMode) {
        final String action = intent.getAction();

        boolean needToShowDialpadChooser = false;

        if (Intent.ACTION_DIAL.equals(action) || Intent.ACTION_VIEW.equals(action)) {
            Uri uri = intent.getData();
            if (uri == null) {
                // ACTION_DIAL or ACTION_VIEW with no data.
                // This behaves basically like ACTION_MAIN: If there's
                // already an active call, bring up an intermediate UI to
                // make the user confirm what they really want to do.
                // Be sure *not* to show the dialpad chooser if this is an
                // explicit "Add call" action, though.
                if (!isAddCallMode && phoneIsInUse()) {
                    needToShowDialpadChooser = true;
                }
            }
        } else if (Intent.ACTION_MAIN.equals(action)) {
            // The MAIN action means we're bringing up a blank dialer
            // (e.g. by selecting the Home shortcut, or tabbing over from
            // Contacts or Call log.)
            //
            // At this point, IF there's already an active call, there's a
            // good chance that the user got here accidentally (but really
            // wanted the in-call dialpad instead).  So we bring up an
            // intermediate UI to make the user confirm what they really
            // want to do.
            if (phoneIsInUse()) {
                // Log.i(TAG, "resolveIntent(): phone is in use; showing dialpad chooser!");
                needToShowDialpadChooser = true;
            }
        }

        return needToShowDialpadChooser;
    }

    private static boolean isAddCallMode(Intent intent) {
        final String action = intent.getAction();
        if (Intent.ACTION_DIAL.equals(action) || Intent.ACTION_VIEW.equals(action)) {
            // see if we are "adding a call" from the InCallScreen; false by default.
            return intent.getBooleanExtra(ADD_CALL_MODE_KEY, false);
        } else {
            return false;
        }
    }

    /**
     * Checks the given Intent and changes dialpad's UI state. For example, if the Intent requires
     * the screen to enter "Add Call" mode, this method will show correct UI for the mode.
     */
    public void configureScreenFromIntent(Intent intent) {
        if (!isLayoutReady()) {
            // This happens typically when parent's Activity#onNewIntent() is called while
            // Fragment#onCreateView() isn't called yet, and thus we cannot configure Views at
            // this point. onViewCreate() should call this method after preparing layouts, so
            // just ignore this call now.
            Log.i(TAG,
                    "Screen configuration is requested before onCreateView() is called. Ignored");
            return;
        }

        boolean needToShowDialpadChooser = false;

        // Be sure *not* to show the dialpad chooser if this is an
        // explicit "Add call" action, though.
        final boolean isAddCallMode = isAddCallMode(intent);
        if (!isAddCallMode) {

            // Don't show the chooser when called via onNewIntent() and phone number is present.
            // i.e. User clicks a telephone link from gmail for example.
            // In this case, we want to show the dialpad with the phone number.
            final boolean digitsFilled = fillDigitsIfNecessary(intent);
            if (!digitsFilled) {
                needToShowDialpadChooser = needToShowDialpadChooser(intent, isAddCallMode);
            }
        }
        showDialpadChooser(needToShowDialpadChooser);
    }

    /**
     * Sets formatted digits to digits field.
     */
    private void setFormattedDigits(String data, String normalizedNumber) {
        // strip the non-dialable numbers out of the data string.
        String dialString = PhoneNumberUtils.extractNetworkPortion(data);
        dialString =
                PhoneNumberUtils.formatNumber(dialString, normalizedNumber, mCurrentCountryIso);
        if (!TextUtils.isEmpty(dialString)) {
            Editable digits = mDigits.getText();
            digits.replace(0, digits.length(), dialString);
            // for some reason this isn't getting called in the digits.replace call above..
            // but in any case, this will make sure the background drawable looks right
            afterTextChanged(digits);
        }
    }

    private void setupKeypad(View fragmentView) {
        int[] buttonIds = new int[] { R.id.one, R.id.two, R.id.three, R.id.four, R.id.five,
                R.id.six, R.id.seven, R.id.eight, R.id.nine, R.id.zero, R.id.star, R.id.pound};
        for (int id : buttonIds) {
            ((DialpadImageButton) fragmentView.findViewById(id)).setOnPressedListener(this);
        }

        // Long-pressing one button will initiate Voicemail.
        fragmentView.findViewById(R.id.one).setOnLongClickListener(this);

        // Long-pressing zero button will enter '+' instead.
        fragmentView.findViewById(R.id.zero).setOnLongClickListener(this);
        fragmentView.findViewById(R.id.star).setOnLongClickListener(this);
        fragmentView.findViewById(R.id.pound).setOnLongClickListener(this);

    }

    @Override
    public void onResume() {
        super.onResume();

        final StopWatch stopWatch = StopWatch.start("Dialpad.onResume");

        // Query the last dialed number. Do it first because hitting
        // the DB is 'slow'. This call is asynchronous.
        queryLastOutgoingCall();

        stopWatch.lap("qloc");

        // retrieve the DTMF tone play back setting.
        mDTMFToneEnabled = Settings.System.getInt(getActivity().getContentResolver(),
                Settings.System.DTMF_TONE_WHEN_DIALING, 1) == 1;

        stopWatch.lap("dtwd");

        // Retrieve the haptic feedback setting.
        mHaptic.checkSystemSetting();

        stopWatch.lap("hptc");

        // if the mToneGenerator creation fails, just continue without it.  It is
        // a local audio signal, and is not as important as the dtmf tone itself.
        synchronized (mToneGeneratorLock) {
            if (mToneGenerator == null) {
                try {
                    mToneGenerator = new ToneGenerator(DIAL_TONE_STREAM_TYPE, TONE_RELATIVE_VOLUME);
                } catch (RuntimeException e) {
                    Log.w(TAG, "Exception caught while creating local tone generator: " + e);
                    mToneGenerator = null;
                }
            }
        }
        stopWatch.lap("tg");
        // Prevent unnecessary confusion. Reset the press count anyway.
        mDialpadPressCount = 0;

        Activity parent = getActivity();
        if (parent instanceof DialtactsActivity) {
            // See if we were invoked with a DIAL intent. If we were, fill in the appropriate
            // digits in the dialer field.
            fillDigitsIfNecessary(parent.getIntent());
        }

        stopWatch.lap("fdin");

        // While we're in the foreground, listen for phone state changes,
        // purely so that we can take down the "dialpad chooser" if the
        // phone becomes idle while the chooser UI is visible.
        TelephonyManager telephonyManager = (TelephonyManager) getActivity().getSystemService(Context.TELEPHONY_SERVICE);
        for (int i = 0; i < MSimTelephonyManager.getDefault().getPhoneCount(); i++) {
            telephonyManager.listen(getPhoneStateListener(i), PhoneStateListener.LISTEN_CALL_STATE);
        }

        stopWatch.lap("tm");

        // Potentially show hint text in the mDigits field when the user
        // hasn't typed any digits yet.  (If there's already an active call,
        // this hint text will remind the user that he's about to add a new
        // call.)
        //
        // TODO: consider adding better UI for the case where *both* lines
        // are currently in use.  (Right now we let the user try to add
        // another call, but that call is guaranteed to fail.  Perhaps the
        // entire dialer UI should be disabled instead.)
        if (phoneIsInUse()) {
            final SpannableString hint = new SpannableString(
                    getActivity().getString(R.string.dialerDialpadHintText));
            hint.setSpan(new RelativeSizeSpan(0.8f), 0, hint.length(), 0);
            mDigits.setHint(hint);
        } else {
            // Common case; no hint necessary.
            mDigits.setHint(null);

            // Also, a sanity-check: the "dialpad chooser" UI should NEVER
            // be visible if the phone is idle!
            showDialpadChooser(false);
        }

        stopWatch.lap("hnt");

        updateDialAndDeleteButtonEnabledState();

        stopWatch.lap("bes");

        stopWatch.stopAndLog(TAG, 50);

        if (!phoneIsInUse()) {
          //add for UX_smart dialer
            setupListView();
            setQueryFilter();
            hideDialPadShowList(false);
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        // Stop listening for phone state changes.
        TelephonyManager telephonyManager = (TelephonyManager) getActivity().getSystemService(Context.TELEPHONY_SERVICE);
        for (int i = 0; i < MSimTelephonyManager.getDefault().getPhoneCount(); i++) {
            telephonyManager.listen(getPhoneStateListener(i), PhoneStateListener.LISTEN_NONE);
        }

        // Make sure we don't leave this activity with a tone still playing.
        stopTone();
        // Just in case reset the counter too.
        mDialpadPressCount = 0;

        synchronized (mToneGeneratorLock) {
            if (mToneGenerator != null) {
                mToneGenerator.release();
                mToneGenerator = null;
            }
        }
        // TODO: I wonder if we should not check if the AsyncTask that
        // lookup the last dialed number has completed.
        mLastNumberDialed = EMPTY_NUMBER;  // Since we are going to query again, free stale number.

        SpecialCharSequenceMgr.cleanup();
        if(mAdapter != null) {
            mAdapter.changeCursor(null);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mClearDigitsOnStop) {
            mClearDigitsOnStop = false;
            mDigits.getText().clear();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(PREF_DIGITS_FILLED_BY_INTENT, mDigitsFilledByIntent);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        // Landscape dialer uses the real actionbar menu, whereas portrait uses a fake one
        // that is created using constructPopupMenu()
        if (ContactsUtils.isLandscape(this.getActivity()) ||
                ViewConfiguration.get(getActivity()).hasPermanentMenuKey() &&
                isLayoutReady() && mDialpadChooser != null) {
            inflater.inflate(R.menu.dialpad_options, menu);
        }
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        // Hardware menu key should be available and Views should already be ready.
        if (ContactsUtils.isLandscape(this.getActivity()) ||
                ViewConfiguration.get(getActivity()).hasPermanentMenuKey() &&
                isLayoutReady() && mDialpadChooser != null) {
            setupMenuItems(menu);
        }
    }

    private void setupMenuItems(Menu menu) {
        final MenuItem callSettingsMenuItem = menu.findItem(R.id.menu_call_settings_dialpad);
        final MenuItem addToContactMenuItem = menu.findItem(R.id.menu_add_contacts);
        final MenuItem twoSecPauseMenuItem = menu.findItem(R.id.menu_2s_pause);
        final MenuItem waitMenuItem = menu.findItem(R.id.menu_add_wait);
        final MenuItem sendMessageMenuItem = menu.findItem(R.id.menu_send_message);
        final MenuItem ipSub1CallMenuItem =menu.findItem(R.id.menu_sub1ipcall);
        final MenuItem ipSub2CallMenuItem =menu.findItem(R.id.menu_sub2ipcall);

        // Check if all the menu items are inflated correctly. As a shortcut, we assume all menu
        // items are ready if the first item is non-null.
        if (callSettingsMenuItem == null) {
            return;
        }

        sub0Moderm = ContactsLib.UsimRecords.getCurModem(SUB0);
        sub1Moderm = ContactsLib.UsimRecords.getCurModem(SUB1);

        final Activity activity = getActivity();
        if (activity != null && ViewConfiguration.get(activity).hasPermanentMenuKey()) {
            // Call settings should be available via its parent Activity.
            callSettingsMenuItem.setVisible(false);
        } else {
            callSettingsMenuItem.setVisible(true);
            callSettingsMenuItem.setIntent(DialtactsActivity.getCallSettingsIntent());
        }

        // We show "add to contacts", "2sec pause", and "add wait" menus only when the user is
        // seeing usual dialpads and has typed at least one digit.
        // We never show a menu if the "choose dialpad" UI is up.
        if (dialpadChooserVisible() || isDigitsEmpty()) {
            addToContactMenuItem.setVisible(false);
            twoSecPauseMenuItem.setVisible(false);
            waitMenuItem.setVisible(false);
            sendMessageMenuItem.setVisible(false);
            if(ipSub1CallMenuItem != null) {
                ipSub1CallMenuItem.setVisible(false);
            }
            if(ipSub2CallMenuItem != null) {
                 ipSub2CallMenuItem.setVisible(false);
            }
        } else {
            final CharSequence digits = mDigits.getText();

            // Put the current digits string into an intent
            addToContactMenuItem.setIntent(getAddToContactIntent(digits));
            addToContactMenuItem.setVisible(true);
            sendMessageMenuItem.setVisible(true);
            if(MSimTelephonyManager.getDefault().isMultiSimEnabled())
            {
                if(sub0Moderm.equals("")){
                       ipSub1CallMenuItem.setVisible(false);
                    }else
                    {
                       ipSub1CallMenuItem.setVisible(true);
                    }
                if(sub1Moderm.equals("")){
                       ipSub2CallMenuItem.setVisible(false);
                    }else
                    {
                       ipSub2CallMenuItem.setVisible(true);
                    }
                ipSub1CallMenuItem.setTitle(getResources().getString(R.string.recentCalls_IPCall_card1));
                ipSub2CallMenuItem.setTitle(getResources().getString(R.string.recentCalls_IPCall_card2));
            }else
            {
                ipSub1CallMenuItem.setVisible(true);
                ipSub2CallMenuItem.setVisible(false);
                ipSub1CallMenuItem.setTitle(getResources().getString(R.string.recentCalls_IPCall));	  
            }
            // Check out whether to show Pause & Wait option menu items
            int selectionStart;
            int selectionEnd;
            String strDigits = digits.toString();

            selectionStart = mDigits.getSelectionStart();
            selectionEnd = mDigits.getSelectionEnd();

            if (selectionStart != -1) {
                if (selectionStart > selectionEnd) {
                    // swap it as we want start to be less then end
                    int tmp = selectionStart;
                    selectionStart = selectionEnd;
                    selectionEnd = tmp;
                }

                if (selectionStart != 0) {
                    // Pause can be visible if cursor is not in the begining
                    twoSecPauseMenuItem.setVisible(true);

                    // For Wait to be visible set of condition to meet
                    waitMenuItem.setVisible(showWait(selectionStart, selectionEnd, strDigits));
                } else {
                    // cursor in the beginning both pause and wait to be invisible
                    twoSecPauseMenuItem.setVisible(false);
                    waitMenuItem.setVisible(false);
                }
            } else {
                twoSecPauseMenuItem.setVisible(true);

                // cursor is not selected so assume new digit is added to the end
                int strLength = strDigits.length();
                waitMenuItem.setVisible(showWait(strLength, strLength, strDigits));
            }
        }
    }

    private static Intent getAddToContactIntent(CharSequence digits) {
        final Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
        intent.putExtra(Insert.PHONE, digits);
        intent.setType(People.CONTENT_ITEM_TYPE);
        return intent;
    }

    private void keyPressed(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_1:
                playTone(ToneGenerator.TONE_DTMF_1, TONE_LENGTH_INFINITE);
                break;
            case KeyEvent.KEYCODE_2:
                playTone(ToneGenerator.TONE_DTMF_2, TONE_LENGTH_INFINITE);
                break;
            case KeyEvent.KEYCODE_3:
                playTone(ToneGenerator.TONE_DTMF_3, TONE_LENGTH_INFINITE);
                break;
            case KeyEvent.KEYCODE_4:
                playTone(ToneGenerator.TONE_DTMF_4, TONE_LENGTH_INFINITE);
                break;
            case KeyEvent.KEYCODE_5:
                playTone(ToneGenerator.TONE_DTMF_5, TONE_LENGTH_INFINITE);
                break;
            case KeyEvent.KEYCODE_6:
                playTone(ToneGenerator.TONE_DTMF_6, TONE_LENGTH_INFINITE);
                break;
            case KeyEvent.KEYCODE_7:
                playTone(ToneGenerator.TONE_DTMF_7, TONE_LENGTH_INFINITE);
                break;
            case KeyEvent.KEYCODE_8:
                playTone(ToneGenerator.TONE_DTMF_8, TONE_LENGTH_INFINITE);
                break;
            case KeyEvent.KEYCODE_9:
                playTone(ToneGenerator.TONE_DTMF_9, TONE_LENGTH_INFINITE);
                break;
            case KeyEvent.KEYCODE_0:
                playTone(ToneGenerator.TONE_DTMF_0, TONE_LENGTH_INFINITE);
                break;
            case KeyEvent.KEYCODE_POUND:
                playTone(ToneGenerator.TONE_DTMF_P, TONE_LENGTH_INFINITE);
                break;
            case KeyEvent.KEYCODE_STAR:
                playTone(ToneGenerator.TONE_DTMF_S, TONE_LENGTH_INFINITE);
                break;
            default:
                break;
        }

        mHaptic.vibrate();
        KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, keyCode);
        mDigits.onKeyDown(keyCode, event);

        // If the cursor is at the end of the text we hide it.
        final int length = mDigits.length();
        if (length == mDigits.getSelectionStart() && length == mDigits.getSelectionEnd()) {
            mDigits.setCursorVisible(false);
        }
    }

    @Override
    public boolean onKey(View view, int keyCode, KeyEvent event) {
        switch (view.getId()) {
            case R.id.digits:
                if (keyCode == KeyEvent.KEYCODE_ENTER) {
                    dialButtonPressed();
                    return true;
                }
                break;
        }
        return false;
    }

    /**
     * When a key is pressed, we start playing DTMF tone, do vibration, and enter the digit
     * immediately. When a key is released, we stop the tone. Note that the "key press" event will
     * be delivered by the system with certain amount of delay, it won't be synced with user's
     * actual "touch-down" behavior.
     */
    @Override
    public void onPressed(View view, boolean pressed) {
        if (DEBUG) Log.d(TAG, "onPressed(). view: " + view + ", pressed: " + pressed);
        if (pressed) {
            switch (view.getId()) {
                case R.id.one: {
                    keyPressed(KeyEvent.KEYCODE_1);
                    break;
                }
                case R.id.two: {
                    keyPressed(KeyEvent.KEYCODE_2);
                    break;
                }
                case R.id.three: {
                    keyPressed(KeyEvent.KEYCODE_3);
                    break;
                }
                case R.id.four: {
                    keyPressed(KeyEvent.KEYCODE_4);
                    break;
                }
                case R.id.five: {
                    keyPressed(KeyEvent.KEYCODE_5);
                    break;
                }
                case R.id.six: {
                    keyPressed(KeyEvent.KEYCODE_6);
                    break;
                }
                case R.id.seven: {
                    keyPressed(KeyEvent.KEYCODE_7);
                    break;
                }
                case R.id.eight: {
                    keyPressed(KeyEvent.KEYCODE_8);
                    break;
                }
                case R.id.nine: {
                    keyPressed(KeyEvent.KEYCODE_9);
                    break;
                }
                case R.id.zero: {
                    keyPressed(KeyEvent.KEYCODE_0);
                    break;
                }
                case R.id.pound: {
                    keyPressed(KeyEvent.KEYCODE_POUND);
                    break;
                }
                case R.id.star: {
                    keyPressed(KeyEvent.KEYCODE_STAR);
                    break;
                }
                default: {
                    Log.wtf(TAG, "Unexpected onTouch(ACTION_DOWN) event from: " + view);
                    break;
                }
            }
            mDialpadPressCount++;
        } else {
            view.jumpDrawablesToCurrentState();
            mDialpadPressCount--;
            if (mDialpadPressCount < 0) {
                // e.g.
                // - when the user action is detected as horizontal swipe, at which only
                //   "up" event is thrown.
                // - when the user long-press '0' button, at which dialpad will decrease this count
                //   while it still gets press-up event here.
                if (DEBUG) Log.d(TAG, "mKeyPressCount become negative.");
                stopTone();
                mDialpadPressCount = 0;
            } else if (mDialpadPressCount == 0) {
                stopTone();
            }
        }
    }

    @Override
    public void onClick(View view) {
        if (DEBUG)Log.d(TAG,"onClick");
        switch (view.getId()) {
            case R.id.deleteButton: {
                keyPressed(KeyEvent.KEYCODE_DEL);
                return;
            }
            case R.id.dialButton: {
                //mHaptic.vibrate();  // Vibrate here too, just like we do for the regular keys
                /*if (getDialButtonLongClicked() == false)
                    dialButtonPressed();
                else
                    setDialButtonLongClicked(false);*/
                return;
            }
            case R.id.digits: {
                if (!isDigitsEmpty()) {
                    mDigits.setCursorVisible(true);
                }
                return;
            }
            //add for UX_smart dialer
            case R.id.cancel_btn: {
                hideDialPadShowList(false);
                listScrollTop();
                return;
            }
            case R.id.filterbutton: {
                if (mDialpad.getVisibility() == View.VISIBLE) {
                    hideDialPadShowList(true);
                }
                return;
            }
            case R.id.add_contact: {
                final CharSequence digits = mDigits.getText();
                startActivity(getAddToContactIntent(digits));
                return;
            }
            default: {
                Log.wtf(TAG, "Unexpected onClick() event from: " + view);
                return;
            }
        }
    }

    public PopupMenu constructPopupMenu(View anchorView) {
        final Context context = getActivity();
        if (context == null) {
            return null;
        }
        final PopupMenu popupMenu = new PopupMenu(context, anchorView);
        final Menu menu = popupMenu.getMenu();
        popupMenu.inflate(R.menu.dialpad_options);
        popupMenu.setOnMenuItemClickListener(this);
        setupMenuItems(menu);
        return popupMenu;
    }

    @Override
    public boolean onLongClick(View view) {
        final Editable digits = mDigits.getText();        
        String networkType = ContactsLib.UsimRecords.getNetWorkType();
        final int id = view.getId();
        switch (id) {
            case R.id.deleteButton: {
                digits.clear();
                // TODO: The framework forgets to clear the pressed
                // status of disabled button. Until this is fixed,
                // clear manually the pressed status. b/2133127
                mDelete.setPressed(false);
                return true;
            }
            case R.id.one: {
                // '1' may be already entered since we rely on onTouch() event for numeric buttons.
                // Just for safety we also check if the digits field is empty or not.
                if (isDigitsEmpty() || TextUtils.equals(mDigits.getText(), "1")) {
                    // We'll try to initiate voicemail and thus we want to remove irrelevant string.
                    removePreviousDigitIfPossible();

                    if (isVoicemailAvailable()) {
                        callVoicemail();
                    } else if (getActivity() != null) {
                        // Voicemail is unavailable maybe because Airplane mode is turned on.
                        // Check the current status and show the most appropriate error message.
                        final boolean isAirplaneModeOn =
                                Settings.System.getInt(getActivity().getContentResolver(),
                                Settings.System.AIRPLANE_MODE_ON, 0) != 0;
                        if (isAirplaneModeOn) {
                            DialogFragment dialogFragment = ErrorDialogFragment.newInstance(
                                    R.string.dialog_voicemail_airplane_mode_message);
                            dialogFragment.show(getFragmentManager(),
                                    "voicemail_request_during_airplane_mode");
                        } else {
                            callVoicemail();
                        }
                    }
                    return true;
                }
                return false;
            }
            case R.id.zero: {
                // Remove tentative input ('0') done by onTouch().
                removePreviousDigitIfPossible();
                keyPressed(KeyEvent.KEYCODE_PLUS);

                // Stop tone immediately and decrease the press count, so that possible subsequent
                // dial button presses won't honor the 0 click any more.
                // Note: this *will* make mDialpadPressCount negative when the 0 key is released,
                // which should be handled appropriately.
                stopTone();
                if (mDialpadPressCount > 0) mDialpadPressCount--;

                return true;
            }
            case R.id.digits: {
                // Right now EditText does not show the "paste" option when cursor is not visible.
                // To show that, make the cursor visible, and return false, letting the EditText
                // show the option by itself.
                mDigits.setCursorVisible(true);
                return false;
            }
            case R.id.dialButton: {
                if (isDigitsEmpty()) {
                    handleDialButtonClickWithEmptyDigits();
                    // This event should be consumed so that onClick() won't do the exactly same
                    // thing.
                    return true;
                } else {
                    return false;
                }
            }
             case R.id.star: {
                if(mDigits.length() >= 12) {
                    mDigits.setTextSize(23.0f);
                }
                
                if(!isDigitsEmpty() && mDigits.getSelectionStart() != 1) {
                               removePreviousDigitIfPossible();
                                if(ContactsLib.isExportProduct()){
                                        keyPressed(KeyEvent.KEYCODE_COMMA);
                                }
                                else{
                                        keyPressed(KeyEvent.KEYCODE_SHIFT_LEFT);
                                        keyPressed(KeyEvent.KEYCODE_P);
                                        keyUp(KeyEvent.KEYCODE_SHIFT_LEFT);
                                }
                                stopTone();
                                if (mDialpadPressCount > 0) mDialpadPressCount--;
                    return true;
                }
            }
            case R.id.pound: {
                if(mDigits.length() >= 12) {
                    mDigits.setTextSize(23.0f);
                }
                if(!isDigitsEmpty() && mDigits.getSelectionStart() != 1) {
                                removePreviousDigitIfPossible();
                                if(ContactsLib.isExportProduct()){
                                        keyPressed(KeyEvent.KEYCODE_SEMICOLON);
                                }
                                else{
                                        keyPressed(KeyEvent.KEYCODE_SHIFT_RIGHT);
                                        //String networkType = ContactsLib.UsimRecords.getNetWorkType();
                                        if(ContactsLib.isSingleMode() &&
                                            TelephonyManager.getDefault().getPhoneType()==TelephonyManager.PHONE_TYPE_CDMA)
                                                {
                                                    keyPressed(KeyEvent.KEYCODE_T);
                                                }
                                        else if(networkType.equals("4,1")) {
                                            keyPressed(KeyEvent.KEYCODE_T);
                                        }
                                        else if(networkType.equals("0,1")) {
                                            keyPressed(KeyEvent.KEYCODE_W);
                                        }
                                        else { // single card
                                            keyPressed(KeyEvent.KEYCODE_W);
                                        }
                                        keyUp(KeyEvent.KEYCODE_SHIFT_LEFT);
                                    }
                                stopTone();
                                if (mDialpadPressCount > 0) mDialpadPressCount--;
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Remove the digit just before the current position. This can be used if we want to replace
     * the previous digit or cancel previously entered character.
     */
    private void removePreviousDigitIfPossible() {
        final Editable editable = mDigits.getText();
        final int currentPosition = mDigits.getSelectionStart();
        if (currentPosition > 0) {
            mDigits.setSelection(currentPosition);
            mDigits.getText().delete(currentPosition - 1, currentPosition);
        }
    }

    public void callVoicemail() {
        startActivity(ContactsUtils.getVoicemailIntent());
        mClearDigitsOnStop = true;
        getActivity().finish();
    }

    public static class ErrorDialogFragment extends DialogFragment {
        private int mTitleResId;
        private int mMessageResId;

        private static final String ARG_TITLE_RES_ID = "argTitleResId";
        private static final String ARG_MESSAGE_RES_ID = "argMessageResId";

        public static ErrorDialogFragment newInstance(int messageResId) {
            return newInstance(0, messageResId);
        }

        public static ErrorDialogFragment newInstance(int titleResId, int messageResId) {
            final ErrorDialogFragment fragment = new ErrorDialogFragment();
            final Bundle args = new Bundle();
            args.putInt(ARG_TITLE_RES_ID, titleResId);
            args.putInt(ARG_MESSAGE_RES_ID, messageResId);
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            mTitleResId = getArguments().getInt(ARG_TITLE_RES_ID);
            mMessageResId = getArguments().getInt(ARG_MESSAGE_RES_ID);
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            if (mTitleResId != 0) {
                builder.setTitle(mTitleResId);
            }
            if (mMessageResId != 0) {
                builder.setMessage(mMessageResId);
            }
            builder.setPositiveButton(android.R.string.ok,
                    new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dismiss();
                            }
                    });
            return builder.create();
        }
    }

    /**
     * In most cases, when the dial button is pressed, there is a
     * number in digits area. Pack it in the intent, start the
     * outgoing call broadcast as a separate task and finish this
     * activity.
     *
     * When there is no digit and the phone is CDMA and off hook,
     * we're sending a blank flash for CDMA. CDMA networks use Flash
     * messages when special processing needs to be done, mainly for
     * 3-way or call waiting scenarios. Presumably, here we're in a
     * special 3-way scenario where the network needs a blank flash
     * before being able to add the new participant.  (This is not the
     * case with all 3-way calls, just certain CDMA infrastructures.)
     *
     * Otherwise, there is no digit, display the last dialed
     * number. Don't finish since the user may want to edit it. The
     * user needs to press the dial button again, to dial it (general
     * case described above).
     */
    public void dialButtonPressed() {
        if (isDigitsEmpty()) { // No number entered.
            handleDialButtonClickWithEmptyDigits();
        } else {
            final String number = mDigits.getText().toString();

            // "persist.radio.otaspdial" is a temporary hack needed for one carrier's automated
            // test equipment.
            // TODO: clean it up.
            if (number != null
                    && !TextUtils.isEmpty(mProhibitedPhoneNumberRegexp)
                    && number.matches(mProhibitedPhoneNumberRegexp)
                    && (SystemProperties.getInt("persist.radio.otaspdial", 0) != 1)) {
                Log.i(TAG, "The phone number is prohibited explicitly by a rule.");
                if (getActivity() != null) {
                    DialogFragment dialogFragment = ErrorDialogFragment.newInstance(
                            R.string.dialog_phone_call_prohibited_message);
                    dialogFragment.show(getFragmentManager(), "phone_prohibited_dialog");
                }

                // Clear the digits just in case.
                mDigits.getText().clear();
            } else {
                final Intent intent = ContactsUtils.getCallIntent(number,
                        (getActivity() instanceof DialtactsActivity ?
                                ((DialtactsActivity)getActivity()).getCallOrigin() : null));
                startActivity(intent);
                mClearDigitsOnStop = true;
                getActivity().finish();
            }
        }
    }

    private void handleDialButtonClickWithEmptyDigits() {
        if (offhookInCdma()) {
            // This is really CDMA specific. On GSM is it possible
            // to be off hook and wanted to add a 3rd party using
            // the redial feature.
            startActivity(newFlashIntent());
        } else {
            if (!TextUtils.isEmpty(mLastNumberDialed)) {
                mDialButtonClickWithEmptyDigits = true;
                // Recall the last number dialed.
                mDigits.setText(mLastNumberDialed);

                // ...and move the cursor to the end of the digits string,
                // so you'll be able to delete digits using the Delete
                // button (just as if you had typed the number manually.)
                //
                // Note we use mDigits.getText().length() here, not
                // mLastNumberDialed.length(), since the EditText widget now
                // contains a *formatted* version of mLastNumberDialed (due to
                // mTextWatcher) and its length may have changed.
                mDigits.setSelection(mDigits.getText().length());
            } else {
                // There's no "last number dialed" or the
                // background query is still running. There's
                // nothing useful for the Dial button to do in
                // this case.  Note: with a soft dial button, this
                // can never happens since the dial button is
                // disabled under these conditons.
                playTone(ToneGenerator.TONE_PROP_NACK);
            }
        }
    }

    /**
     * Plays the specified tone for TONE_LENGTH_MS milliseconds.
     */
    private void playTone(int tone) {
        playTone(tone, TONE_LENGTH_MS);
    }

    /**
     * Play the specified tone for the specified milliseconds
     *
     * The tone is played locally, using the audio stream for phone calls.
     * Tones are played only if the "Audible touch tones" user preference
     * is checked, and are NOT played if the device is in silent mode.
     *
     * The tone length can be -1, meaning "keep playing the tone." If the caller does so, it should
     * call stopTone() afterward.
     *
     * @param tone a tone code from {@link ToneGenerator}
     * @param durationMs tone length.
     */
    private void playTone(int tone, int durationMs) {
        // if local tone playback is disabled, just return.
        if (!mDTMFToneEnabled) {
            return;
        }

        // Also do nothing if the phone is in silent mode.
        // We need to re-check the ringer mode for *every* playTone()
        // call, rather than keeping a local flag that's updated in
        // onResume(), since it's possible to toggle silent mode without
        // leaving the current activity (via the ENDCALL-longpress menu.)
        AudioManager audioManager =
                (AudioManager) getActivity().getSystemService(Context.AUDIO_SERVICE);
        int ringerMode = audioManager.getRingerMode();
        if ((ringerMode == AudioManager.RINGER_MODE_SILENT)
            || (ringerMode == AudioManager.RINGER_MODE_VIBRATE)) {
            return;
        }

        synchronized (mToneGeneratorLock) {
            if (mToneGenerator == null) {
                Log.w(TAG, "playTone: mToneGenerator == null, tone: " + tone);
                return;
            }

            // Start the new tone (will stop any playing tone)
            mToneGenerator.startTone(tone, durationMs);
        }
    }

    /**
     * Stop the tone if it is played.
     */
    private void stopTone() {
        // if local tone playback is disabled, just return.
        if (!mDTMFToneEnabled) {
            return;
        }
        synchronized (mToneGeneratorLock) {
            if (mToneGenerator == null) {
                Log.w(TAG, "stopTone: mToneGenerator == null");
                return;
            }
            mToneGenerator.stopTone();
        }
    }

    /**
     * Brings up the "dialpad chooser" UI in place of the usual Dialer
     * elements (the textfield/button and the dialpad underneath).
     *
     * We show this UI if the user brings up the Dialer while a call is
     * already in progress, since there's a good chance we got here
     * accidentally (and the user really wanted the in-call dialpad instead).
     * So in this situation we display an intermediate UI that lets the user
     * explicitly choose between the in-call dialpad ("Use touch tone
     * keypad") and the regular Dialer ("Add call").  (Or, the option "Return
     * to call in progress" just goes back to the in-call UI with no dialpad
     * at all.)
     *
     * @param enabled If true, show the "dialpad chooser" instead
     *                of the regular Dialer UI
     */
    private void showDialpadChooser(boolean enabled) {
        // Check if onCreateView() is already called by checking one of View objects.
        if (!isLayoutReady()) {
            return;
        }

        if (enabled) {
            // Log.i(TAG, "Showing dialpad chooser!");
            if (mDigitsContainer != null) {
                mDigitsContainer.setVisibility(View.GONE);
            } else {
                // mDigits is not enclosed by the container. Make the digits field itself gone.
                mDigits.setVisibility(View.GONE);
            }
            if (mDialpad != null) mDialpad.setVisibility(View.GONE);
            if (mListoutside != null) mListoutside.setVisibility(View.GONE);
            if (mDialButtonContainer != null) mDialButtonContainer.setVisibility(View.GONE);

            mDialpadChooser.setVisibility(View.VISIBLE);

            // Instantiate the DialpadChooserAdapter and hook it up to the
            // ListView.  We do this only once.
            if (mDialpadChooserAdapter == null) {
                mDialpadChooserAdapter = new DialpadChooserAdapter(getActivity());
            }
            mDialpadChooser.setAdapter(mDialpadChooserAdapter);
        } else {
            // Log.i(TAG, "Displaying normal Dialer UI.");
            if (mDigitsContainer != null) {
                mDigitsContainer.setVisibility(View.VISIBLE);
            } else {
                mDigits.setVisibility(View.VISIBLE);
            }
            if (mDialpad != null) mDialpad.setVisibility(View.VISIBLE);
            if (mListoutside != null) mListoutside.setVisibility(View.VISIBLE);
            if (mDialButtonContainer != null) mDialButtonContainer.setVisibility(View.VISIBLE);
            mDialpadChooser.setVisibility(View.GONE);
        }
    }

    /**
     * @return true if we're currently showing the "dialpad chooser" UI.
     */
    private boolean dialpadChooserVisible() {
        return mDialpadChooser.getVisibility() == View.VISIBLE;
    }

    /**
     * Simple list adapter, binding to an icon + text label
     * for each item in the "dialpad chooser" list.
     */
    private static class DialpadChooserAdapter extends BaseAdapter {
        private LayoutInflater mInflater;

        // Simple struct for a single "choice" item.
        static class ChoiceItem {
            String text;
            Bitmap icon;
            int id;

            public ChoiceItem(String s, Bitmap b, int i) {
                text = s;
                icon = b;
                id = i;
            }
        }

        // IDs for the possible "choices":
        static final int DIALPAD_CHOICE_USE_DTMF_DIALPAD = 101;
        static final int DIALPAD_CHOICE_RETURN_TO_CALL = 102;
        static final int DIALPAD_CHOICE_ADD_NEW_CALL = 103;

        private static final int NUM_ITEMS = 3;
        private ChoiceItem mChoiceItems[] = new ChoiceItem[NUM_ITEMS];

        public DialpadChooserAdapter(Context context) {
            // Cache the LayoutInflate to avoid asking for a new one each time.
            mInflater = LayoutInflater.from(context);

            // Initialize the possible choices.
            // TODO: could this be specified entirely in XML?

            // - "Use touch tone keypad"
            mChoiceItems[0] = new ChoiceItem(
                    context.getString(R.string.dialer_useDtmfDialpad),
                    BitmapFactory.decodeResource(context.getResources(),
                                                 R.drawable.ic_dialer_fork_tt_keypad),
                    DIALPAD_CHOICE_USE_DTMF_DIALPAD);

            // - "Return to call in progress"
            mChoiceItems[1] = new ChoiceItem(
                    context.getString(R.string.dialer_returnToInCallScreen),
                    BitmapFactory.decodeResource(context.getResources(),
                                                 R.drawable.ic_dialer_fork_current_call),
                    DIALPAD_CHOICE_RETURN_TO_CALL);

            // - "Add call"
            mChoiceItems[2] = new ChoiceItem(
                    context.getString(R.string.dialer_addAnotherCall),
                    BitmapFactory.decodeResource(context.getResources(),
                                                 R.drawable.ic_dialer_fork_add_call),
                    DIALPAD_CHOICE_ADD_NEW_CALL);
        }

        @Override
        public int getCount() {
            return NUM_ITEMS;
        }

        /**
         * Return the ChoiceItem for a given position.
         */
        @Override
        public Object getItem(int position) {
            return mChoiceItems[position];
        }

        /**
         * Return a unique ID for each possible choice.
         */
        @Override
        public long getItemId(int position) {
            return position;
        }

        /**
         * Make a view for each row.
         */
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            // When convertView is non-null, we can reuse it (there's no need
            // to reinflate it.)
            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.dialpad_chooser_list_item, null);
            }

            TextView text = (TextView) convertView.findViewById(R.id.text);
            text.setText(mChoiceItems[position].text);

            ImageView icon = (ImageView) convertView.findViewById(R.id.icon);
            icon.setImageBitmap(mChoiceItems[position].icon);

            return convertView;
        }
    }

    /**
     * Handle clicks from the dialpad chooser.
     */
    @Override
    public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
        DialpadChooserAdapter.ChoiceItem item =
                (DialpadChooserAdapter.ChoiceItem) parent.getItemAtPosition(position);
        int itemId = item.id;
        switch (itemId) {
            case DialpadChooserAdapter.DIALPAD_CHOICE_USE_DTMF_DIALPAD:
                // Log.i(TAG, "DIALPAD_CHOICE_USE_DTMF_DIALPAD");
                // Fire off an intent to go back to the in-call UI
                // with the dialpad visible.
                returnToInCallScreen(true);
                break;

            case DialpadChooserAdapter.DIALPAD_CHOICE_RETURN_TO_CALL:
                // Log.i(TAG, "DIALPAD_CHOICE_RETURN_TO_CALL");
                // Fire off an intent to go back to the in-call UI
                // (with the dialpad hidden).
                returnToInCallScreen(false);
                break;

            case DialpadChooserAdapter.DIALPAD_CHOICE_ADD_NEW_CALL:
                // Log.i(TAG, "DIALPAD_CHOICE_ADD_NEW_CALL");
                // Ok, guess the user really did want to be here (in the
                // regular Dialer) after all.  Bring back the normal Dialer UI.
                showDialpadChooser(false);
                break;

            default:
                Log.w(TAG, "onItemClick: unexpected itemId: " + itemId);
                break;
        }
    }

    /**
     * Returns to the in-call UI (where there's presumably a call in
     * progress) in response to the user selecting "use touch tone keypad"
     * or "return to call" from the dialpad chooser.
     */
    private void returnToInCallScreen(boolean showDialpad) {
        try {			
            if(MSimTelephonyManager.getDefault().isMultiSimEnabled()){
                ITelephonyMSim telephonyServiceMSim = getMSimTelephonyService();
                if (telephonyServiceMSim != null) telephonyServiceMSim.showCallScreenWithDialpad(showDialpad);
            }else{
                ITelephony phone = getTelephonyService();
                if (phone != null) phone.showCallScreenWithDialpad(showDialpad);
            }
        } catch (RemoteException e) {
            Log.w(TAG, "phone.showCallScreenWithDialpad() failed", e);
        }

        // Finally, finish() ourselves so that we don't stay on the
        // activity stack.
        // Note that we do this whether or not the showCallScreenWithDialpad()
        // call above had any effect or not!  (That call is a no-op if the
        // phone is idle, which can happen if the current call ends while
        // the dialpad chooser is up.  In this case we can't show the
        // InCallScreen, and there's no point staying here in the Dialer,
        // so we just take the user back where he came from...)
        getActivity().finish();
    }

    /**
     * @return true if the phone is "in use", meaning that at least one line
     *              is active (ie. off hook or ringing or dialing).
     */
    public static boolean phoneIsInUse() {
        boolean phoneInUse = false;
        try {
            if(MSimTelephonyManager.getDefault().isMultiSimEnabled()){
                ITelephonyMSim telephonyServiceMSim = getMSimTelephonyService();
                if (telephonyServiceMSim != null) phoneInUse = !telephonyServiceMSim.isIdle(0) || !telephonyServiceMSim.isIdle(1);
            }else{
                ITelephony phone = getTelephonyService();
                if (phone != null) phoneInUse = !phone.isIdle();
            }
        } catch (RemoteException e) {
            Log.w(TAG, "phone.isIdle() failed", e);
        }
        return phoneInUse;
    }

    private static ITelephonyMSim getMSimTelephonyService() {
        ITelephonyMSim telephonyServiceMSim = ITelephonyMSim.Stub.asInterface(
                ServiceManager.checkService(Context.MSIM_TELEPHONY_SERVICE));
        if (telephonyServiceMSim == null) {
            Log.w(TAG, "Unable to find ITelephony interface.");
        }
        return telephonyServiceMSim;
    }

    private static ITelephony getTelephonyService() {
        ITelephony telephonyService = ITelephony.Stub.asInterface(
                ServiceManager.checkService(Context.TELEPHONY_SERVICE));
        if (telephonyService == null) {
            Log.w(TAG, "Unable to find ITelephony interface.");
        }
        return telephonyService;
    }

    private boolean offhookInCdma() {
        try {
            if(MSimTelephonyManager.getDefault().isMultiSimEnabled()){
                ITelephonyMSim telephonyServiceMSim = getMSimTelephonyService();
                if (telephonyServiceMSim != null) return telephonyServiceMSim.isOffhook(0) && telephonyServiceMSim.getActivePhoneType(0) == TelephonyManager.PHONE_TYPE_CDMA;
            }else{
                ITelephony phone = getTelephonyService();
                if (phone != null) return phone.isOffhook() && phone.getActivePhoneType() == TelephonyManager.PHONE_TYPE_CDMA;
            }
        } catch (RemoteException e) {
            Log.w(TAG, "phone.getActivePhoneType() failed", e);
        }
        return false;
    }
//
//    /**
//     * @return true if the phone is a CDMA phone type
//     */
//    private boolean phoneIsCdma() {
//        boolean isCdma = false;
//        try {
//            ITelephony phone = ITelephony.Stub.asInterface(ServiceManager.checkService("phone"));
//            if (phone != null) {
//                isCdma = (phone.getActivePhoneType() == TelephonyManager.PHONE_TYPE_CDMA);
//            }
//        } catch (RemoteException e) {
//            Log.w(TAG, "phone.getActivePhoneType() failed", e);
//        }
//        return isCdma;
//    }
//
//    /**
//     * @return true if the phone state is OFFHOOK
//     */
//    private boolean phoneIsOffhook() {
//        boolean phoneOffhook = false;
//        try {
//            ITelephony phone = ITelephony.Stub.asInterface(ServiceManager.checkService("phone"));
//            if (phone != null) phoneOffhook = phone.isOffhook();
//        } catch (RemoteException e) {
//            Log.w(TAG, "phone.isOffhook() failed", e);
//        }
//        return phoneOffhook;
//    }

    /**
     * Returns true whenever any one of the options from the menu is selected.
     * Code changes to support dialpad options
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_2s_pause:
                updateDialString(",");
                return true;
            case R.id.menu_add_wait:
                updateDialString(";");
                return true;
            case R.id.menu_sub1ipcall:
                if(!isDigitsEmpty())
                    getIPCallIntent(mDigits.getText(), 0);
                return true;
            case R.id.menu_sub2ipcall:
                if(!isDigitsEmpty())
                    getIPCallIntent(mDigits.getText(), 1);
                return true;
            case R.id.menu_send_message:
                if(!isDigitsEmpty())
                    startActivity(getSendMessageIntent(mDigits.getText()));
                return true;
            default:
                return false;
        }
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        return onOptionsItemSelected(item);
    }
    private static Intent getSendMessageIntent(CharSequence digits) {
        Intent intent = null;
        Uri numberUri = null;
        numberUri = Uri.fromParts("sms", digits.toString(), null);
        if (null != numberUri) {
           intent  = new Intent(Intent.ACTION_SENDTO, numberUri);
        }
        return intent;
    }

    private void getIPCallIntent(CharSequence digits, int sub) {
        String ipnumber = null;
        String number = digits.toString();
        
        number = number.replace('P',',');
        number = number.replace('W',';');
        number = number.replace('T',';');
        number = number.replace('(',' ');
        number = number.replace(')',' ');
        number = number.replace('-',' ');
        number = number.replace('.',' ');

        if(number.startsWith("+86")){
            number = number.substring(3);
        }

        if(sub == 0) {
                ipnumber = Settings.System.getString(getActivity().getContentResolver(), Settings.System.IPCALL_PREFIX[0]);
                Log.d(TAG," sub_0  ip number is " + ipnumber);
        }
        else if(sub == 1) {
                ipnumber = Settings.System.getString(getActivity().getContentResolver(), Settings.System.IPCALL_PREFIX[1]);
                Log.d(TAG," sub_1  ip number is " + ipnumber);
        }
        
        if(ipnumber != null) {        
            number = ipnumber + number ;
        }else
        {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle(R.string.callFailure);
            builder.setNegativeButton(android.R.string.cancel, null);
            builder.setMessage(R.string.ipnum_not_set);
            builder.create().show();
            return ;
        }

        //if (telManager.SIM_STATE_ABSENT == telManager.getSimState()) {
        //    return;
        //}

       /*  for(int i=0;i<number.length();i++){
            if (isValidDialerNumber(number.charAt(i)) == false) {
                //showDialerErrorDialog();
                return ;
            }
        }*/
        
        final Intent intent = new Intent(Intent.ACTION_CALL_PRIVILEGED,
                                         Uri.fromParts("tel", number, null));
        intent.putExtra("subscription", sub);                                 
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        if (getActivity() instanceof DialtactsActivity) {
            intent.putExtra(DialtactsActivity.EXTRA_CALL_ORIGIN,
                    DialtactsActivity.CALL_ORIGIN_DIALTACTS);
        }
        startActivity(intent);
    }

    /**
     * Updates the dial string (mDigits) after inserting a Pause character (,)
     * or Wait character (;).
     */
    private void updateDialString(String newDigits) {
        int selectionStart;
        int selectionEnd;

        // SpannableStringBuilder editable_text = new SpannableStringBuilder(mDigits.getText());
        int anchor = mDigits.getSelectionStart();
        int point = mDigits.getSelectionEnd();

        selectionStart = Math.min(anchor, point);
        selectionEnd = Math.max(anchor, point);

        Editable digits = mDigits.getText();
        if (selectionStart != -1) {
            if (selectionStart == selectionEnd) {
                // then there is no selection. So insert the pause at this
                // position and update the mDigits.
                digits.replace(selectionStart, selectionStart, newDigits);
            } else {
                digits.replace(selectionStart, selectionEnd, newDigits);
                // Unselect: back to a regular cursor, just pass the character inserted.
                mDigits.setSelection(selectionStart + 1);
            }
        } else {
            int len = mDigits.length();
            digits.replace(len, len, newDigits);
        }
    }

    /**
     * Update the enabledness of the "Dial" and "Backspace" buttons if applicable.
     */
    private void updateDialAndDeleteButtonEnabledState() {
        final boolean digitsNotEmpty = !isDigitsEmpty();

        if (mDialButton != null) {
            // On CDMA phones, if we're already on a call, we *always*
            // enable the Dial button (since you can press it without
            // entering any digits to send an empty flash.)
            if (offhookInCdma()) {
                mDialButton.setEnabled(true);
            } else {
                // Common case: GSM, or CDMA but not on a call.
                // Enable the Dial button if some digits have
                // been entered, or if there is a last dialed number
                // that could be redialed.
                mDialButton.setEnabled(digitsNotEmpty ||
                        !TextUtils.isEmpty(mLastNumberDialed));
            }
        }
        mDelete.setEnabled(digitsNotEmpty);
    }

    /**
     * Check if voicemail is enabled/accessible.
     *
     * @return true if voicemail is enabled and accessibly. Note that this can be false
     * "temporarily" after the app boot.
     * @see MSimTelephonyManager#getVoiceMailNumber()
     */
    private boolean isVoicemailAvailable() {
        boolean promptEnabled = Settings.Global.getInt(getActivity().getContentResolver(),
                Settings.Global.MULTI_SIM_VOICE_PROMPT, 0) == 1;
        Log.d(TAG, "prompt enabled :  "+ promptEnabled);
        if (promptEnabled) {
            return hasVMNumber();
        } else {
            try {
                mSubscription = MSimTelephonyManager.getDefault().getPreferredVoiceSubscription();
                if (MSimTelephonyManager.getDefault().isMultiSimEnabled()) {
                    return (MSimTelephonyManager.getDefault().
                            getVoiceMailNumber(mSubscription) != null);
                } else {
                    return (TelephonyManager.getDefault().getVoiceMailNumber() != null);
                }
            } catch (SecurityException se) {
                // Possibly no READ_PHONE_STATE privilege.
                Log.e(TAG, "SecurityException is thrown. Maybe privilege isn't sufficient.");
            }
        }
        return false;
    }

    private boolean hasVMNumber() {
        boolean hasVMNum = false;
        int phoneCount = MSimTelephonyManager.getDefault().getPhoneCount();
        for (int i = 0; i < phoneCount; i++) {
            try {
                hasVMNum = MSimTelephonyManager.getDefault().getVoiceMailNumber(i) != null;
            } catch (SecurityException se) {
                // Possibly no READ_PHONE_STATE privilege.
                Log.e(TAG, "SecurityException is thrown. Maybe privilege isn't sufficient.");
            }
            if (hasVMNum) {
                break;
            }
        }
        return hasVMNum;
    }

    /**
     * This function return true if Wait menu item can be shown
     * otherwise returns false. Assumes the passed string is non-empty
     * and the 0th index check is not required.
     */
    private static boolean showWait(int start, int end, String digits) {
        if (start == end) {
            // visible false in this case
            if (start > digits.length()) return false;

            // preceding char is ';', so visible should be false
            if (digits.charAt(start - 1) == ';') return false;

            // next char is ';', so visible should be false
            if ((digits.length() > start) && (digits.charAt(start) == ';')) return false;
        } else {
            // visible false in this case
            if (start > digits.length() || end > digits.length()) return false;

            // In this case we need to just check for ';' preceding to start
            // or next to end
            if (digits.charAt(start - 1) == ';') return false;
        }
        return true;
    }

    /**
     * @return true if the widget with the phone number digits is empty.
     */
    private boolean isDigitsEmpty() {
        return mDigits.length() == 0;
    }

    /**
     * Starts the asyn query to get the last dialed/outgoing
     * number. When the background query finishes, mLastNumberDialed
     * is set to the last dialed number or an empty string if none
     * exists yet.
     */
    private void queryLastOutgoingCall() {
        mLastNumberDialed = EMPTY_NUMBER;
        CallLogAsync.GetLastOutgoingCallArgs lastCallArgs =
                new CallLogAsync.GetLastOutgoingCallArgs(
                    getActivity(),
                    new CallLogAsync.OnLastOutgoingCallComplete() {
                        @Override
                        public void lastOutgoingCall(String number) {
                            // TODO: Filter out emergency numbers if
                            // the carrier does not want redial for
                            // these.
                            mLastNumberDialed = number;
                            updateDialAndDeleteButtonEnabledState();
                        }
                    });
        mCallLog.getLastOutgoingCall(lastCallArgs);
    }

    private Intent newFlashIntent() {
        final Intent intent = ContactsUtils.getCallIntent(EMPTY_NUMBER);
        intent.putExtra(EXTRA_SEND_EMPTY_FLASH, true);
        intent.putExtra(SUBSCRIPTION_KEY, mSubscription);
        return intent;
    }

    //add for UX_enhance_dial_button start
    class GlowPadViewMethods implements GlowPadView.OnTriggerListener{
        private final GlowPadView mGlowPadView;

        GlowPadViewMethods(GlowPadView glowPadView) {
            mGlowPadView = glowPadView;
        }

        public void updateResources() {

        }

        public void onGrabbed(View v, int handle) {
        }

        public void onReleased(View v, int handle) {
            setDialWidgetVisibility(false);
        }

        public void onTrigger(View v, int target) {
            final int resId = mGlowPadView.getResourceIdForTarget(target);
            switch (resId) {
                case R.drawable.ic_dial_slot1:
                        dialWidgetSwitched(MSimConstants.SUB1);
                    break;

                case R.drawable.ic_dial_slot2:
                        dialWidgetSwitched(MSimConstants.SUB2);
                    break;
            }
        }

        public void onGrabbedStateChange(View v, int handle) {
        }

        public void onFinishFinalAnimation() {
        }
   }

    public View getDialButton(){
       return (mDialButton == null)?null:mDialButton;
    }

    public GlowPadView getGlowPadView(){
        return (mDialWidget == null)?null:mDialWidget;
    }

    public int getDialWidgetVisibility(){
        return (mDialWidget == null)?View.GONE:mDialWidget.getVisibility();
    }

    public void setDialWidgetVisibility(boolean visible){
        if (visible){
            mDialWidget.setVisibility(View.VISIBLE);
            mDialButton.setVisibility(View.GONE);
        }else{
            mDialWidget.setVisibility(View.GONE);
            mDialButton.setVisibility(View.VISIBLE);
        }
    }

    public boolean canShowDialWidget(){
        if (isDigitsEmpty())
            return false;

        if (phoneIsInUse())
            return false;

        int phoneCount = MSimTelephonyManager.getDefault().getPhoneCount();
        if(phoneCount == 1)
            {
                return false;
            }
        for (int i = 0; i < phoneCount; i++) {
             if (!MSimTelephonyManager.getDefault().isValidSimState(i))
                 return false;
        }
        return true;
    }

    public void dialWidgetSwitched(int subscription) {
        if (isDigitsEmpty()) { // No number entered.
            handleDialButtonClickWithEmptyDigits();
        } else {
            final String number = mDigits.getText().toString();

            // "persist.radio.otaspdial" is a temporary hack needed for one carrier's automated
            // test equipment.
            // TODO: clean it up.
            if (number != null
                    && !TextUtils.isEmpty(mProhibitedPhoneNumberRegexp)
                    && number.matches(mProhibitedPhoneNumberRegexp)
                    && (SystemProperties.getInt("persist.radio.otaspdial", 0) != 1)) {
                Log.i(TAG, "The phone number is prohibited explicitly by a rule.");
                if (getActivity() != null) {
                    DialogFragment dialogFragment = ErrorDialogFragment.newInstance(
                            R.string.dialog_phone_call_prohibited_message);
                    dialogFragment.show(getFragmentManager(), "phone_prohibited_dialog");
                }

                // Clear the digits just in case.
                mDigits.getText().clear();
            } else {
                final Intent intent = ContactsUtils.getCallIntent(number,
                        (getActivity() instanceof DialtactsActivity ?
                                ((DialtactsActivity)getActivity()).getCallOrigin() : null));
                intent.putExtra("dial_widget_switched", subscription);
                startActivity(intent);
                mClearDigitsOnStop = true;
                getActivity().finish();
            }
        }
     }

    public void transferCoordinates(){
        int[] locationButton = new int[2];
        int[] locationWidget = new int[2];

        if (mDialButton != null && mDialButton.getVisibility() == View.VISIBLE){
            mDialButton.getLocationOnScreen(locationButton);
        }
        setDialWidgetVisibility(true);
        if (mDialWidget != null && mDialWidget.getVisibility() == View.VISIBLE){
            mDialWidget.getLocationOnScreen(locationWidget);
        }
        transferX = locationButton[0] - locationWidget[0];
        transferY = locationButton[1] - locationWidget[1];
    }

    public int getTransferCoordinateX(){
        return transferX;
    }

    public int getTransferCoordinateY(){
        return transferY;
    }

    public void setDialButtonLongClicked(boolean longClicked){
        mDialButtonLongClicked = longClicked;
    }

    public boolean getDialButtonLongClicked(){
        return mDialButtonLongClicked;
    }

    public DialpadCling getDialpadCling(){
        return mDialpadCling;
    }
//add for UX_enhance_dial_button end

//add for UX_smart dialer start
    private Animation showAction;
    private Animation hideAction;
    private ContactItemListAdapter mAdapter;
    private Cursor mCursor;
    //private View mListlayout;
    private View mListoutside;
    private View mCountButton;
    private Button mCancel;
    private View mAddContact;
    private TextView mAddContactText;
    private TextView mCountView;
    static final String[] CONTACTS_SUMMARY_FILTER_NUMBER_PROJECTION = new String[] {
        ("_id"),
        ("normalized_number"),
        ("display_name"),
        ("photo_id"),
        ("lookup"),
    };
    static final int QUERY_CONTACT_ID = 0;
    static final int QUERY_NUMBER = 1;
    static final int QUERY_DISPLAY_NAME = 2;
    static final int QUERY_PHOTO_ID = 3;
    static final int QUERY_LOOKUP_KEY = 4;
    public static final Uri CONTENT_SMART_DIALER_FILTER_URI =
            Uri.withAppendedPath(ContactsContract.AUTHORITY_URI, "smart_dialer_filter");

    final static class ContactListItemCache {
        public CharArrayBuffer nameBuffer = new CharArrayBuffer(128);
        public CharArrayBuffer dataBuffer = new CharArrayBuffer(128);
        public CharArrayBuffer highlightedTextBuffer = new CharArrayBuffer(128);
        public TextWithHighlighting textWithHighlighting;
        public CharArrayBuffer phoneticNameBuffer = new CharArrayBuffer(128);
    }

    private void setQueryFilter(){
        listScrollTop();
        if(mAdapter != null) {
            String filterString = getTextFilter();
            if (TextUtils.isEmpty(filterString)){
                mAdapter.changeCursor(null);
            } else {
                Filter filter = mAdapter.getFilter();
                filter.filter(getTextFilter());
            }
        }
    }

    private void hideDialPadShowList(boolean isHide) {
        if (isHide) {
            mDialpad.startAnimation(hideAction);
            mDialpad.setVisibility(View.GONE);
            mCountButton.setVisibility(View.GONE);
            mCancel.setVisibility(View.VISIBLE);
            mCancel.setText(android.R.string.cancel);
        } else{
            if (!mDialpad.isShown()){
                mDialpad.startAnimation(showAction);
                mDialpad.setVisibility(View.VISIBLE);
            }
            if(mCursor !=null && !mCursor.isClosed() && mCursor.getCount() > 0){
                mCountButton.setVisibility(View.VISIBLE);
            }
            mCancel.setVisibility(View.GONE);
            listScrollTop();
        }
    }
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        showAction = new TranslateAnimation(
                Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 0.0f,
                Animation.RELATIVE_TO_SELF, 1.0f, Animation.RELATIVE_TO_SELF, 0.0f);
        showAction.setDuration(100);
        hideAction = new TranslateAnimation(
                Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 0.0f,
                Animation.RELATIVE_TO_SELF, 0.0f, Animation.RELATIVE_TO_SELF, 1.0f);
        hideAction.setDuration(100);
        setupListView();

        configureScreenFromIntent(getActivity().getIntent());
    }

    public void onConfigurationChanged(Configuration newConfig) {
        if(null != mAddContactText){
            mAddContactText.setText(R.string.non_phone_add_to_contacts);
        }
        if (mClingText != null){
            mClingText.setText(R.string.dialpad_cling_multi_sim_call);
        }
        super.onConfigurationChanged(newConfig);
        // When locale changed,  update the list view.
        mDialpadChooserAdapter = new DialpadChooserAdapter(getActivity());
        mDialpadChooser.setAdapter(mDialpadChooserAdapter);
    }

    private void keyPressedSilence(int keyCode) {
        KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, keyCode);
        mDigits.onKeyDown(keyCode, event);
    }

    private void keyUp(int keyCode) {
        KeyEvent event = new KeyEvent(KeyEvent.ACTION_UP, keyCode);
        mDigits.onKeyUp(keyCode, event);
    }

    private void setDigitsPhoneByString(String phone) {
        char cNum;
        int iNum;
        playTone(ToneGenerator.TONE_DTMF_0);
        mDigits.setText(phone);
        hideDialPadShowList(false);
        listScrollTop();
    }

    public void onListItemClick(ListView l, View v, int position, long id) {
        final Cursor cursor = (Cursor)mAdapter.getItem(position);
        String phone;
        phone = cursor.getString(QUERY_NUMBER);

        setDigitsPhoneByString(phone);

    }

    private void listScrollTop(){
        this.getListView().post(new Runnable(){
            public void run() {
                if(DialpadFragment.this.isResumed()){
                    DialpadFragment.this.getListView().setSelection(0);
                }
            }
        });
    }

    private void setupListView() {
         final ListView list = getListView();
         mAdapter = new ContactItemListAdapter(getActivity());
         setListAdapter(mAdapter);
         list.setOnCreateContextMenuListener(this);
         list.setOnScrollListener(new AbsListView.OnScrollListener() {
             @Override
             public void onScrollStateChanged(AbsListView view, int scrollState) {
                if (mDialpad.getVisibility() == View.VISIBLE && AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL == scrollState) {
                    hideDialPadShowList(true);
                }else if(mDialpad.getVisibility() == View.VISIBLE && AbsListView.OnScrollListener.SCROLL_STATE_IDLE == scrollState){
                    list.setSelection(0);
                }
             }
             @Override
             public void onScroll(AbsListView view, int firstVisibleItem,
                            int visibleItemCount, int totalItemCount) {
             }
         });
        list.setSaveEnabled(false);
    }

    private String getTextFilter() {
        if (mDigits != null) {
            //filter useless space
            return mDigits.getText().toString().replaceAll("[^0123456789+]", "");
        }
        return null;
    }

    private Cursor doFilter(String filter) {
        if(getActivity() != null) {
            final ContentResolver resolver = getActivity().getContentResolver();
            Builder builder = CONTENT_SMART_DIALER_FILTER_URI.buildUpon();
            builder.appendQueryParameter("filter", filter);
            mCursor = resolver.query(builder.build(), CONTACTS_SUMMARY_FILTER_NUMBER_PROJECTION, null, null,  null);

            return mCursor;
        }
        else {
            return null;
        }
    }

    private final class ContactItemListAdapter extends CursorAdapter {
            private SectionIndexer mIndexer;
            private boolean mLoading = true;
            private CharSequence mUnknownNameText;
            private boolean mDisplayPhotos = false;
            private boolean mDisplayCallButton = false;
            private boolean mDisplayAdditionalData = true;
            private boolean mDisplaySectionHeaders = true;
            private Cursor mSuggestionsCursor;
            private int mSuggestionsCursorCount;
            private LayoutInflater inflater;

            int[] getStartEnd(String s, int start, int end){
                int[] offset = new int[2];

                for (int i=0;i<s.length();i++){
                    char c = s.charAt(i);
                    if (!(c >= '0' && c <= '9' || c == '+' || c >= 'a' && c <= 'z')){
                        if (i<=start){
                            start ++;
                            end ++;
                        }else if(i>start && i<=end){
                            end ++;
                        }
                    }
                }
                if(start > s.length()){
                    start = s.length();
                }
                if(end > s.length()){
                    end = s.length();
                }
                offset[0] = start;
                offset[1] = end;
                return offset;
            }

            String getNumberFormChar(char c){
                if(c >= 'a' && c <= 'c'){
                    return "2";
                }else if(c >= 'd' && c <= 'f'){
                    return "3";
                }else if(c >= 'g' && c <= 'i'){
                    return "4";
                }else if(c >= 'j' && c <= 'l'){
                    return "5";
                }else if(c >= 'm' && c <= 'o'){
                    return "6";
                }else if(c >= 'p' && c <= 's'){
                    return "7";
                }else if(c >= 't' && c <= 'v'){
                    return "8";
                }else if(c >= 'w' && c <= 'z'){
                    return "9";
                }else if('0' <= c && c <= '9'){
                    return "" + c;
                }else{
                    return "";
                }
            }

            String getNameNumber(String name){
                String number = "";
                String nameLow = name.toLowerCase();
                for(int i=0;i<nameLow.length();i++){
                    char c = nameLow.charAt(i);
                    number = number + getNumberFormChar(c);
                }
                return number;
            }

            void setTextViewSearchByNumber(char[] charName, int size, Cursor cursor, TextView nameView, TextView dataView) {
                boolean bCheckFinished = false;

                String strNameView = String.copyValueOf(charName, 0, size);
                String strDataViewPhone;
                strDataViewPhone = cursor.getString(QUERY_NUMBER);

                String inputNum = getTextFilter();
                String phoneNum = strDataViewPhone != null ? strDataViewPhone.replaceAll("[^0123456789+]", "") : null;

                if (phoneNum != null && inputNum != null && phoneNum.contains(inputNum)){
                    int start, end;
                    start = phoneNum.indexOf(inputNum);
                    end = start + inputNum.length();
                    int[] offset = getStartEnd(strDataViewPhone, start, end);
                    SpannableStringBuilder style=new SpannableStringBuilder(strDataViewPhone);
                    style.setSpan(new BackgroundColorSpan(0xFF33B5E5),offset[0],offset[1],Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    dataView.setText(style);
                } else {
                    dataView.setText(strDataViewPhone);
                }

                String nameNum = getNameNumber(strNameView);
                if(nameNum != null && inputNum != null && nameNum.contains(inputNum)){
                    int start, end;
                    start = nameNum.indexOf(inputNum);
                    end = start + inputNum.length();
                    int[] offset = getStartEnd(strNameView.toLowerCase(), start, end);
                    SpannableStringBuilder style=new SpannableStringBuilder(strNameView);
                    style.setSpan(new BackgroundColorSpan(0xFF33B5E5),offset[0],offset[1],Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    nameView.setText(style);
                }else{
                    nameView.setText(strNameView);//here haven't highlight name
                }
            }

            public void setSuggestionsCursor(Cursor cursor) {
                if (mSuggestionsCursor != null) {
                    mSuggestionsCursor.close();
                }
                mSuggestionsCursor = cursor;
                mSuggestionsCursorCount = cursor == null ? 0 : cursor.getCount();
            }

            public ContactItemListAdapter(Context context) {
                super(context, null, false);
                inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            }


            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (!mDataValid) {
                    throw new IllegalStateException("this should only be called when the cursor is valid");
                }

                boolean showingSuggestion;
                Cursor cursor;
                if (mSuggestionsCursorCount != 0 && position < mSuggestionsCursorCount + 2) {
                    showingSuggestion = true;
                    cursor = mSuggestionsCursor;
                } else {
                    showingSuggestion = false;
                    cursor = mCursor;
                }

                if (!cursor.moveToPosition(position)) {
                    throw new IllegalStateException("couldn't move cursor to position " + position);
                }

                boolean newView;
                View v;
                if (convertView == null || convertView.getTag() == null) {
                    newView = true;
                    v = newView(mContext, cursor, parent);
                } else {
                    newView = false;
                    v = convertView;
                }
                bindView(v, mContext, cursor);
                return v;
            }

            public void bindView(View itemView, Context context, Cursor cursor) {
                final ContactListItemView view = (ContactListItemView)itemView;
                final ContactListItemCache cache = (ContactListItemCache) view.getTag();

                cursor.copyStringToBuffer(QUERY_DISPLAY_NAME, cache.nameBuffer);

                TextView nameView = view.getNameTextView();
                TextView dataView = view.getDataView();
                int size = cache.nameBuffer.sizeCopied;
                if (size != 0) {
                    setTextViewSearchByNumber(cache.nameBuffer.data, size, cursor, nameView,dataView);
                } else {
                    nameView.setText(mUnknownNameText);
                }

                final long contactId = cursor.getLong(QUERY_CONTACT_ID);
                final String lookupKey = cursor.getString(QUERY_LOOKUP_KEY);
                long photoId = 0;
                if (!cursor.isNull(QUERY_PHOTO_ID)) {
                    photoId = cursor.getLong(QUERY_PHOTO_ID);
                }

                QuickContactBadge photo = view.getQuickContact();

                 photo.assignContactFromPhone(cursor.getString(QUERY_NUMBER), true);

                ContactPhotoManager.getInstance(mContext).loadThumbnail(photo, photoId, false);
                view.setPresence(null);


            }

            @Override
            public View newView(Context arg0, Cursor arg1, ViewGroup arg2) {
                final ContactListItemView view = new ContactListItemView(getActivity(),null);
                view.setPhotoPosition(PhotoPosition.LEFT);
                view.setTag(new ContactListItemCache());
                view.setQuickContactEnabled(true);
                return view;
            }

                /**
                 * Run the query on a helper thread. Beware that this code does not run
                 * on the main UI thread!
                 */
            @Override
            public Cursor runQueryOnBackgroundThread(CharSequence constraint) {
                return doFilter(constraint.toString());
            }
            protected String getQuantityText(int count, int zeroResourceId, int pluralResourceId) {
                if (count == 0) {
                    return getString(zeroResourceId);
                } else {
                    String format = getResources().getQuantityText(pluralResourceId, count).toString();
                    return String.format(format, count);
                }
            }

            public void changeCursor(Cursor cursor) {
                try {
                    if(isDigitsEmpty()){
                        mCountButton.setVisibility(View.GONE);
                        DialpadFragment.this.getListView().setVisibility(View.GONE);
                        hideDialPadShowList(false);
                    }else if (cursor != null && cursor.moveToFirst()){
                        mCountButton.setVisibility(View.VISIBLE);
                        DialpadFragment.this.getListView().setVisibility(View.VISIBLE);
                    } else {
                        mCountButton.setVisibility(View.GONE);
                        DialpadFragment.this.getListView().setVisibility(View.GONE);
                        hideDialPadShowList(false);
                    }
                    if (mDialpad.isShown() && !isDigitsEmpty() && cursor != null && cursor.getCount() > 0){
                        mCountButton.setVisibility(View.VISIBLE);
                        mCountView.setText(cursor.getCount()+"");
                        mCountView.invalidate();
                    } else {
                        mCountButton.setVisibility(View.GONE);
                    }
                } 
                catch(Exception e) {
                    e.printStackTrace();
                }
                super.changeCursor(cursor);
            }
        }
    //add for UX_smart dialer end
}
