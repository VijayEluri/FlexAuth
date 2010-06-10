package com.chrisheald.flexauth;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;

import org.apache.http.client.ClientProtocolException;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.Handler;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;

public class FlexAuth extends Activity {
	static final int MENU_ADD_AUTH = 1;	
	static final int MENU_INFO = 2;
	static final int DIALOG_EDIT = 1;
	static final int DIALOG_DATA_MISSING = 2;
	static final int DIALOG_BAD_SERIAL = 3;
	static final int VIEW_ID = 1;
	static final int DELETE_ID = 2;
	public static final Exception InvalidSerialException = null;
	private Handler mHandler = new Handler();
	
	private String ENROLL_URL;
	private String PUB_KEY;
	
	private SQLiteDatabase db, readDb;
	private TokenAdapter tAdapter;
	
	private class TokenAdapter extends ArrayAdapter<Token> {
		private ArrayList<Token> items;
		
		public TokenAdapter(Context context, int textViewResourceId, ArrayList<Token> items) {
            super(context, textViewResourceId, items);
            this.items = items;
		}
		
		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View v = convertView;
			if (v == null) {
				LayoutInflater vi = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				v = vi.inflate(R.layout.token_row, null);
			}
			Token o = items.get(position);
			if (o != null) {
				TextView tt = (TextView) v.findViewById(R.id.toptext);
				TextView bt = (TextView) v.findViewById(R.id.bottomtext);
				if (tt != null) {
					tt.setText(o.name);
				}
				if (bt != null) {
					try {
						bt.setText(o.getPassword());
					} catch (InvalidKeyException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (NoSuchAlgorithmException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
			return v;
		}
	}
	
	private class AccountDialog extends Dialog {
		private Button save;
		private Button generate;
		private EditText accountName;
		private EditText serial;
		private EditText secret;
		
		public AccountDialog(Context c, CharSequence title) {
			super(c);
			this.setTitle(title);
		}
		
		protected void onCreate(Bundle savedInstanceState) {
			super.onCreate(savedInstanceState);
			setContentView(R.layout.new_account);
			save = (Button)findViewById(R.id.saveToken);
			generate = (Button)findViewById(R.id.requestToken);
			accountName = (EditText)findViewById(R.id.accountName);
			serial = (EditText)findViewById(R.id.tokenSerial);
			secret = (EditText)findViewById(R.id.tokenSecret);
			accountName.setText("");
			serial.setText("");
			secret.setText("");
			
			save.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					accountName = (EditText)findViewById(R.id.accountName);
					serial = (EditText)findViewById(R.id.tokenSerial);
					secret = (EditText)findViewById(R.id.tokenSecret);
					
					String n = accountName.getText().toString().trim();
					String ss = serial.getText().toString().trim();
					String sl = secret.getText().toString().trim();
					String[] args = {n, ss, sl};				
					db.execSQL("INSERT INTO accounts (name, serial, secret) VALUES (?, ?, ?)", args);
					updateTokenList();
					dismiss();
				}
			});
			
			generate.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					Token t = new Token(ENROLL_URL, PUB_KEY);
					try {
						t.generate();
						EditText serial = (EditText)findViewById(R.id.tokenSerial);
						EditText secret = (EditText)findViewById(R.id.tokenSecret);
						secret.setText(t.secret);
						serial.setText(t.serial);					
					} catch (NoSuchAlgorithmException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (ClientProtocolException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (InvalidSerialException e) {
						dismiss();
						showDialog(DIALOG_BAD_SERIAL);
					}
				}
			});
		}
	}
	
	private int lastMod = -1;
	private Runnable mUpdateTimeTask = new Runnable() {
		public void run() {
			int mod = (int)(System.currentTimeMillis() % 30000L);
			ProgressBar pb = (ProgressBar)findViewById(R.id.ProgressBar01);
			if(lastMod == -1 || mod < lastMod) {
				lastMod = mod;
				updateTokenList();
			}
			if(pb != null) pb.setProgress(mod);
			mHandler.postDelayed(this, 500);
		}
	};

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ENROLL_URL = getResources().getString(R.string.enroll_url);
        PUB_KEY = getResources().getString(R.string.pubkey);
        
        AppDb dbHelper = new AppDb(this);
        db = dbHelper.write_db();
        readDb = dbHelper.read_db();

        viewList();
        
		mHandler.removeCallbacks(mUpdateTimeTask);
		mHandler.postDelayed(mUpdateTimeTask, 500);
    }
    
    private void updateTokenList() {
    	Cursor c = null;
		try {
			c = readDb.rawQuery(
					"select * from accounts order by id asc", null);
			c.moveToFirst();
			tAdapter.clear();
			tAdapter.notifyDataSetChanged();
			while (!c.isAfterLast()) {
				String name = c.getString(c.getColumnIndexOrThrow("name"));
				String secret = c.getString(c.getColumnIndexOrThrow("secret"));
				String serial = c.getString(c.getColumnIndexOrThrow("serial"));
				Token t = new Token(ENROLL_URL, PUB_KEY, name, secret, serial);
				t._id = c.getInt(c.getColumnIndexOrThrow("id"));
				c.moveToNext();
				tAdapter.add(t);
			}
			tAdapter.notifyDataSetChanged();
		} finally {
			if(c != null) c.close();
    	}
    }
    
    private void viewToken(long tokenId) {
    	Token t = tAdapter.items.get((int)tokenId);
    	Intent intent = new Intent(this, TokenView.class);

    	intent.putExtra("secret", t.secret);
    	intent.putExtra("serial", t.serial);
    	String latestAuthCode = null;
    	try {
    		latestAuthCode = t.getPassword();
		} catch (InvalidKeyException e) {
			latestAuthCode = "<error>";
		} catch (NoSuchAlgorithmException e) {
			latestAuthCode = "<error>";
		}    	
    	intent.putExtra("auth", latestAuthCode);
    	startActivity(intent);
    }
    
    private void viewList() {
        setContentView(R.layout.main);
        ListView lv = (ListView)findViewById(R.id.tokenList);
        tAdapter = new TokenAdapter(this, R.layout.token_row, new ArrayList<Token>());
        lv.setAdapter(tAdapter);
        lv.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
				viewToken(arg3);				
			}
        });        
        registerForContextMenu(lv);
        updateTokenList();
    }
    
    private void destroyToken(long tokenId) {
    	final Token t = tAdapter.items.get((int)tokenId);
    	if(t._id != -1) {
	    	new AlertDialog.Builder(this)	    	
	    	.setIcon(R.drawable.shocked)
	    	.setTitle(R.string.delete_confirm)
	    	.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
	    		public void onClick(DialogInterface dialog, int which) {
	    			Object[] args = {t._id};
	    			db.execSQL("DELETE FROM accounts WHERE id = ?", args);
	    			updateTokenList();
	    		}
	    	})
	    	.setNegativeButton(R.string.no, null)	    	
	    	.show();
    	}
    }
    
    public boolean onCreateOptionsMenu(Menu menu) {
    	menu.add(0, MENU_ADD_AUTH, 0, "Add Account").setIcon(android.R.drawable.ic_menu_add);
    	menu.add(0, MENU_INFO, 0, "Info").setIcon(android.R.drawable.ic_menu_info_details);
    	return true;
    }
    
    public boolean onOptionsItemSelected(MenuItem item) {
    	switch(item.getItemId()) {
    	case MENU_ADD_AUTH:
    		showDialog(DIALOG_EDIT);
    		return true;
    	case MENU_INFO:
    		InputStream is;
    		String msg = "";
			try {
				is = this.getAssets().open("license.txt");
	            if (is != null) {
	                StringBuilder sb = new StringBuilder();
	                String line;
	
	                try {
	                    BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
	                    while ((line = reader.readLine()) != null) {
	                        sb.append(line).append("\n");
	                    }
	                } finally {
	                    is.close();
	                }
	                msg = sb.toString();
	            }
			} catch (IOException e) {
				msg = "<Unable to open license file>";
			}
	            
            
    		new AlertDialog.Builder(this)
    		.setMessage(msg)
    		.setTitle("License & Information")
    		.setIcon(android.R.drawable.ic_menu_info_details)
    		.show();
    		return true;
    	}
    	return false;
    }
    
    protected Dialog onCreateDialog(int id) {
    	Dialog dialog;
    	CharSequence msg;
    	switch(id) {
    	case DIALOG_EDIT:
    		dialog = new AccountDialog(this, "New Account");
    		break;
    	case DIALOG_DATA_MISSING:
    		msg = getResources().getText(R.string.invalid_data);
    		dialog = new AlertDialog.Builder(this).setMessage(msg).create();
    	case DIALOG_BAD_SERIAL:
    		msg = getResources().getText(R.string.invalid_data);
    		dialog = new AlertDialog.Builder(this).setMessage(msg).create();
    	default:
    		dialog = null;
    	}
    	return dialog;
    }
    
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
    	super.onCreateContextMenu(menu, v, menuInfo);
    	menu.add(0, VIEW_ID, 0, "View Details");
    	menu.add(0, DELETE_ID, 0,  "Delete Token");
    }
    
    public boolean onContextItemSelected(MenuItem item) {
    	AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
    	long id = tAdapter.getItemId(info.position);
    	switch (item.getItemId()) {
    	case VIEW_ID:
    		viewToken(id);
    		return true;
    	case DELETE_ID:
    		destroyToken(id);
    		return true;
    	default:
    		super.onContextItemSelected(item);
    	}
    	return false;
    }
}