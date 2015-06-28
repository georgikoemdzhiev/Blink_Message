package koemdzhiev.com.blinkmessage.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.parse.FindCallback;
import com.parse.ParseException;
import com.parse.ParseFile;
import com.parse.ParseInstallation;
import com.parse.ParseObject;
import com.parse.ParseQuery;
import com.parse.ParseRelation;
import com.parse.ParseUser;
import com.parse.SaveCallback;

import java.util.ArrayList;
import java.util.List;

import koemdzhiev.com.blinkmessage.R;
import koemdzhiev.com.blinkmessage.adapters.UserAdapter;
import koemdzhiev.com.blinkmessage.utils.FileHelper;
import koemdzhiev.com.blinkmessage.utils.ParseConstants;


public class RecipientsActivity extends Activity {
    private static final String TAG = RecipientsActivity.class.getSimpleName();
    protected List<ParseUser> mFriends;
    protected ParseUser mCurrentUser;
    protected MenuItem mSendMenuItem;
    protected Uri mMediaUri;
    protected String mFileType;
    protected GridView mGridView;

    protected ParseRelation<ParseUser> mFriendsRelation;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.user_grid);

        mGridView = (GridView)findViewById(R.id.friendsGrid);
        mGridView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        TextView emptyTextView = (TextView) findViewById(android.R.id.empty);
        mGridView.setEmptyView(emptyTextView);
        mGridView.setOnItemClickListener(mOnItemClickListener);
        getActionBar().setDisplayHomeAsUpEnabled(true);

        mMediaUri = getIntent().getData();
        mFileType = getIntent().getExtras().getString(ParseConstants.KEY_FILE_TYPE);
    }
    @Override
    public void onResume() {
        super.onResume();

        mCurrentUser = ParseUser.getCurrentUser();
        mFriendsRelation = mCurrentUser.getRelation(ParseConstants.KEY_FRIENDS_RELATION);
        setProgressBarIndeterminateVisibility(true);

        ParseQuery<ParseUser> query = mFriendsRelation.getQuery();
        query.addAscendingOrder(ParseConstants.KEY_USERNAME);
        query.findInBackground(new FindCallback<ParseUser>() {
            @Override
            public void done(List<ParseUser> friends, ParseException e) {
                setProgressBarIndeterminateVisibility(false);
                if (e == null) {
                    //Success!
                    mFriends = friends;
                    int i = 0;
                    String[] userNames = new String[mFriends.size()];
                    for (ParseUser user : mFriends) {
                        userNames[i] = user.getUsername();
                        i++;
                    }
                    if(mGridView.getAdapter() == null) {
                        UserAdapter adapter = new UserAdapter(RecipientsActivity.this, mFriends);
                        mGridView.setAdapter(adapter);
                    }else{
                        ((UserAdapter)mGridView.getAdapter()).refill(mFriends);
                    }
                } else {
                    Log.e(TAG, e.getMessage());
                    AlertDialog.Builder builder = new AlertDialog.Builder(RecipientsActivity.this);
                    //e.getMesssage = says useful information about the error
                    builder.setMessage(e.getMessage());
                    builder.setTitle(R.string.error_title);
                    builder.setPositiveButton(android.R.string.ok, null);

                    AlertDialog dialog = builder.create();
                    dialog.show();
                }
            }
        });
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_recipients, menu);
        mSendMenuItem = menu.getItem(0);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_send) {
            ParseObject message = createMessage();
            if(message == null){
                //error
                AlertDialog.Builder  builder = new AlertDialog.Builder(this);
                builder.setTitle(getString(R.string.error_selecting_tile_title));
                builder.setMessage(getString(R.string.error_selecting_file));
                builder.setPositiveButton(android.R.string.ok, null);
                AlertDialog dialog = builder.create();
                dialog.show();
            }else {
                //send
                send(message);
            }

            finish();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }


    protected  ParseObject createMessage(){
        ParseObject message = new ParseObject(ParseConstants.CLASS_MESSAGES);
        message.put(ParseConstants.KEY_SENDER_ID,ParseUser.getCurrentUser().getObjectId());
        message.put(ParseConstants.KEY_SENDER_NAME,ParseUser.getCurrentUser().getUsername());
        message.put(ParseConstants.KEY_RECIPIENT_IDS, getRecipientIds());
        message.put(ParseConstants.KEY_FILE_TYPE, mFileType);

        byte[] fileBytes = FileHelper.getByteArrayFromFile(this, mMediaUri);
        if(fileBytes == null){
            return null;
        }else{
            if(mFileType.equals(ParseConstants.TYPE_IMAGE)){
                fileBytes = FileHelper.reduceImageForUpload(fileBytes);
            }

            String fileName = FileHelper.getFileName(this,mMediaUri,mFileType);
            ParseFile file = new ParseFile(fileName,fileBytes);
            message.put(ParseConstants.KEY_FILE, file);

            return message;
        }

    }

    protected ArrayList<String> getRecipientIds(){
        ArrayList<String> recipientIds = new ArrayList<>();
        for(int i = 0;i < mGridView.getCount(); i++){
            if(mGridView.isItemChecked(i)) {
                recipientIds.add(mFriends.get(i).getObjectId());
            }
        }

        return recipientIds;
    }

    protected void send(ParseObject message){
        message.saveInBackground(new SaveCallback() {
            @Override
            public void done(ParseException e) {
                if(e == null){
                    //sueccess!
                    Toast.makeText(RecipientsActivity.this,getString(R.string.success_message),Toast.LENGTH_LONG).show();
                    sendPushNotification();
                }else {
                    AlertDialog.Builder  builder = new AlertDialog.Builder(RecipientsActivity.this);
                    builder.setTitle(getString(R.string.error_selecting_tile_title));
                    builder.setMessage(getString(R.string.error_sending_message));
                    builder.setPositiveButton(android.R.string.ok, null);
                    AlertDialog dialog = builder.create();
                    dialog.show();
                }
            }
        });
    }

    private void sendPushNotification() {
        ParseQuery<ParseInstallation> query = ParseInstallation.getQuery();
        query.whereContainedIn(ParseConstants.KEY_USER_ID,getRecipientIds());

    }

    protected AdapterView.OnItemClickListener mOnItemClickListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            if(mGridView.getCheckedItemCount()> 0) {
                mSendMenuItem.setVisible(true);
            }else{
                mSendMenuItem.setVisible(false);
            }
            ImageView imageView = (ImageView)view.findViewById(R.id.checkImageView);
            if(mGridView.isItemChecked(position)){
                //add recipient
                imageView.setVisibility(View.VISIBLE);

            }else{
                //remove recipient
                imageView.setVisibility(View.INVISIBLE);
            }
        }
    };
}
