package com.graceplayer.activity;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.graceplayer.graceplayer.R;
import com.graceplayer.http.LrcFileDownLoad;

public class LrcActivity extends Activity {

	private TextView tv_lrc;
	private int status;
	// 广播接收器
	private StatusChangedReceiver receiver;
	private LinearLayout linearLayout;

	private String lrc_data = null;
	private String filename = null;
	private String musicName = null;
	private String musicArtist = null;

	private AsyncDownLoad asyncDownLoad;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);
		setContentView(R.layout.lrc);

		tv_lrc = (TextView) findViewById(R.id.lrc_tv_lrc);
		linearLayout = (LinearLayout) findViewById(R.id.lrc_linearLayout1);

		bindStatusChangedReceiver();
		tv_lrc.setText("当前没有播放歌曲！");
		// 检查状态
		sendBroadcastOnCommand(MusicService.COMMAND_CHECK_IS_PLAYING);
	}

	/** 绑定广播接收器 */
	private void bindStatusChangedReceiver() {
		receiver = new StatusChangedReceiver();
		IntentFilter filter = new IntentFilter(
				MusicService.BROADCAST_MUSICSERVICE_UPDATE_STATUS);
		registerReceiver(receiver, filter);
	}

	private void sendBroadcastOnCommand(int command) {
		Intent intent = new Intent(MusicService.BROADCAST_MUSICSERVICE_CONTROL);
		intent.putExtra("command", command);
		sendBroadcast(intent);
	}

	/** 内部类，只处理播放状态的广播 */
	class StatusChangedReceiver extends BroadcastReceiver {
		public void onReceive(Context context, Intent intent) {
			// 获取播放器状态
			status = intent.getIntExtra("status", -1);
			switch (status) {
			case MusicService.STATUS_PLAYING:
				musicArtist = intent.getStringExtra("musicArtist");
				musicName = intent.getStringExtra("musicName");
				LrcActivity.this
						.setTitle("歌词:" + musicName + " " + musicArtist);
				try {
					get_lrc(LrcActivity.this);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				break;

			}
		}
	}

	class AsyncDownLoad extends AsyncTask<String, Integer, String> {
		// 执行时调用该方法
		@Override
		protected String doInBackground(String... arg0) {
			// TODO Auto-generated method stub
			String url = null;
			try {
				url = LrcFileDownLoad.getSongLRCUrl(arg0[0], arg0[1]);
				lrc_data = LrcFileDownLoad.getHtmlCode(url);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				System.out.println(e.getMessage());
			}
			return lrc_data;
		}

		// 任务执行前执行该方法
		@Override
		protected void onPreExecute() {
			// TODO Auto-generated method stub
			super.onPreExecute();
			tv_lrc.setText("搜索歌词中...");
		}

		// 任务结束时执行该方法
		@Override
		protected void onPostExecute(String result) {
			// TODO Auto-generated method stub
			super.onPostExecute(result);
			if (result == null) {
				tv_lrc.setText("当前没有歌词");
			} else 
			{
				try {
					// 写入文件
					FileOutputStream outputStream = LrcActivity.this.openFileOutput(filename, Context.MODE_PRIVATE);
					outputStream.write(result.getBytes());
					outputStream.close();
				} catch (FileNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				String string = drawLrcWord(filename);
				tv_lrc.setText(string);
			}
		}
	}

	@Override
	protected void onDestroy() {
		// TODO Auto-generated method stub
		super.onDestroy();
		unregisterReceiver(receiver);
		if(asyncDownLoad != null && !asyncDownLoad.isCancelled())
		{
			asyncDownLoad.cancel(true);
		}
	}
	private void get_lrc(Context context) throws IOException {
		String[] files = context.fileList();							//本程序内部储存空间的文件列表
		if (musicName != null) {
			filename = musicName + ".lrc";
			List<String> fileList = Arrays.asList(files);
			if (fileList.contains(filename)) {								//如果存在本地歌词，直接读出并显示，否则下载歌词
				Log.i("TAG", "has lrc file!");
				String string = drawLrcWord(filename);
				tv_lrc.setText(string);
			} else {
				// 判断网络状态
				ConnectivityManager cwjManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
				NetworkInfo info = cwjManager.getActiveNetworkInfo();
				// 网络可用则进行下载
				if (info != null && info.isAvailable()) {
					Log.i("TAG", "has not lrc file!");
					asyncDownLoad = new AsyncDownLoad();
					asyncDownLoad.execute(LrcFileDownLoad.LRC_SEARCH_URL,musicName);
				} else {
					Toast.makeText(getApplicationContext(),"当前网络不给力哦，请检测网络配置！", Toast.LENGTH_LONG).show();
				}
			}
		}
	}
	private String drawLrcWord(String filename) {
		String lrc_word = "";
		Pattern pattern = Pattern.compile("\\[\\d{2}:\\d{2}.\\d{2}\\]");
		try {
			File file = new File(getApplicationContext().getFilesDir() + "/"
					+ filename);
			System.out.println(file.getPath());
			BufferedReader reader = new BufferedReader(new FileReader(file));
			String line = "";

			while ((line = reader.readLine()) != null) {
				Matcher m = pattern.matcher(line);
				line = m.replaceAll("");
				line = line.replace("[ti:", "");
				line = line.replace("[ar:", "");
				line = line.replace("[al:", "");
				line = line.replace("[by:", "");
				line = line.replace("[i:", "");
				line = line.replace("]", "");
				line = line.contains("offset")?"":line;
                line = line.replace("url","歌词来源");  
                line = line.replace("null","");  
				lrc_word += line+"\n";
				
			}
			return lrc_word;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
	
}
