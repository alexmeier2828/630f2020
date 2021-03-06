package com.AlexMeier.regroup.messaging;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.animation.ObjectAnimator;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.AlexMeier.regroup.R;
import com.AlexMeier.regroup.profile.ProfileData;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.HashMap;

public class ChatRoom extends AppCompatActivity {
    private static final String TAG = "CHAT_ROOM";
    private int scrollerID;
    private FragmentManager fragmentManager;
    FirebaseUser user;
    FirebaseAuth mAuth;
    GroupChatManager groupChatManager;
    LocalBroadcastManager mLocalBroadcastManager;
    BroadcastReceiver broadcastReceiver;

    GroupChatResponse currentGroup;

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chatroom);
        fragmentManager = getSupportFragmentManager();
        //initialize conversation
        mAuth = FirebaseAuth.getInstance();
        GroupChatAPI.joinGroup(mAuth).addOnCompleteListener(new OnCompleteListener<GroupChatResponse>() {
            @Override
            public void onComplete(@NonNull Task<GroupChatResponse> task) {
                if(!task.isSuccessful()){
                    Log.e(TAG, "Failed to get group chat response: " + task.getException());
                }else{
                    subscribeToChat(task.getResult());
                    ProgressBar spinner = findViewById(R.id.spinner);
                    spinner.setVisibility(View.GONE);
                }

            }
        });

        //handle send button
        final EditText messageEditor = findViewById(R.id.message);
        messageEditor.setOnEditorActionListener(
                new TextView.OnEditorActionListener() {
                    @Override
                    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                        if(actionId == EditorInfo.IME_ACTION_SEND){
                            if(messageEditor.getText().toString() != ""){
                                sendMessage();
                            }
                            return true;
                        }
                        return false;
                    }
                }
        );

        //set keyboard send button visible
        messageEditor.setImeOptions(EditorInfo.IME_ACTION_SEND);
        messageEditor.setRawInputType(InputType.TYPE_CLASS_TEXT);

        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                groupChatManager.updateGroup((String) intent.getExtras().get("message"));
            }
        };

        mLocalBroadcastManager = LocalBroadcastManager.getInstance(this);
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(MyFirebaseMessagingService.GROUP_UPDATE);
        mLocalBroadcastManager.registerReceiver(broadcastReceiver, intentFilter);

    }




    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
    public void sendMessage(){
        //read text from message editor
        EditText editText = findViewById(R.id.message);
        String messageText = editText.getText().toString();
        editText.setText("");

        //do not send empty message
        if(!messageText.isEmpty()){
            //post message to board
            addMessageToBoard(new Message(messageText, "You", true));

            //firestream send message
            groupChatManager.sendMessage(messageText);
        }
    }


    //TODO set message board to lock until user has been subscribed to a group

    /**
     * Adds a new message bubble to the message board with a smooth animation
     * @param message
     */
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
    public void addMessageToBoard(Message message){
        if(!getSupportFragmentManager().isDestroyed()){
            String messageText = message.getMessageBody();
            MessageFragment messageFragment = MessageFragment.newInstance(message.getMessageAuthor(), messageText, message.isUserIsSender());
            FragmentTransaction transaction = this.getSupportFragmentManager().beginTransaction().add(R.id.fragment_holder, messageFragment);
            transaction.commit();
            //scroll to bottom of scroll view with smooth animation
            ScrollView scrollView = findViewById(R.id.chatroom_scroll);
            LinearLayout scrollLinearLayout = findViewById(R.id.fragment_holder);
            ObjectAnimator scrollAnimation = ObjectAnimator.ofInt(scrollView, "scrollY", scrollLinearLayout.getBottom());
            scrollAnimation.setDuration(1000);
            scrollAnimation.start();
        }
    }


    /**
     * Subscribes to updates to group meta data
     * @param group
     */
    private void subscribeToChat(GroupChatResponse group){
        //subscribe to chat message topic
        groupChatManager = new GroupChatManager(this, group) {
            @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
            @Override
            public void onMessageReceived(Message message) {
                addMessageToBoard(message);
            }
        };
    }

    /**
     * Called when activity switch happens
     */
    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if(keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0){
            leaveGroupDialog();
            return true;
        } else {
            return super.onKeyDown(keyCode, event);
        }
    }

    private void leaveGroupDialog(){
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
        final TextView leaveGroupWarning = new TextView(this);
        leaveGroupWarning.setText("Do you want to leave this group?");
        dialogBuilder.setView(leaveGroupWarning);
        dialogBuilder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                findViewById(R.id.spinner).setVisibility(View.INVISIBLE);
                groupChatManager.leaveGroup().addOnCompleteListener(new OnCompleteListener() {
                    @Override
                    public void onComplete(@NonNull Task task) {
                        finish();
                    }
                });
            }
        });

        dialogBuilder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        dialogBuilder.show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.chatroom_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch(item.getItemId()){
            case R.id.view_profiles:
                if(groupChatManager.getGroupMemberProfiles().size() > 0 ){
                    profileViewer();
                } else {
                    Toast warning = Toast.makeText(this, "The group is empty!", Toast.LENGTH_LONG);
                    warning.setGravity(Gravity.TOP, 0,0);
                    warning.show();
                }

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void profileViewer() {
        HashMap<String, ProfileData> profiles= groupChatManager.getGroupMemberProfiles();

        ArrayList<String> uids = new ArrayList<>();
        for (String uid: profiles.keySet()
             ) {
            uids.add(uid);
        }
        Intent intent = new Intent(this, UserList.class);
        Bundle b = new Bundle();
        b.putStringArrayList("users", uids);
        intent.putExtras(b);
        startActivity(intent);
    }

    @Override
    protected void onStop() {
        super.onStop();
        mLocalBroadcastManager.unregisterReceiver(broadcastReceiver);
    }
}