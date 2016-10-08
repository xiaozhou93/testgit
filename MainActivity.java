package com.graceplayer.activity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.SimpleAdapter;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.graceplayer.data.Music;
import com.graceplayer.data.MusicList;
import com.graceplayer.graceplayer.R;
import com.graceplayer.model.PropertyBean;

public class MainActivity extends Activity {
	
	// 进度条控制常量
	private static final int PROGRESS_INCREASE = 0;
	private static final int PROGRESS_PAUSE = 1;
	private static final int PROGRESS_RESET = 2;
	
	//播放模式常量
	private static final int MODE_LIST_SEQUENCE= 0;
	private static final int MODE_SINGLE_CYCLE = 1;
	private static final int MODE_LIST_CYCLE = 2;
	private int playmode;
	
	// 显示组件
	private TextView tv_current_time;
	private TextView tv_total_time;
	private ImageButton imgBtn_Previous;
	private ImageButton imgBtn_PlayOrPause;
	private ImageButton imgBtn_Stop;
	private ImageButton imgBtn_Next;
	private SeekBar seekBar;
	private ListView listView;
	private RelativeLayout root_Layout;

	// 当前歌曲的持续时间和当前位置，作用于进度条
	private int total_time;
	private int curent_time;
	// 当前歌曲的序号，下标从0开始
	private int number;
	// 播放状态
	private int status;
	// 广播接收器
	private StatusChangedReceiver receiver;
	// 更新进度条的Handler
	private Handler seekBarHandler;
	
	//歌曲列表对象
	private ArrayList<Music> musicArrayList;
	
	//退出判断标记
	private static Boolean isExit = false; 
	
	//音量控制
	private TextView tv_vol;
	private SeekBar  seekbar_vol;
	
	//睡眠模式相关组件，标识常量
	private ImageView iv_sleep;
	private Timer timer_sleep ;
	private static final boolean NOTSLEEP = false;
	private static final boolean ISSLEEP = true;
	//默认的睡眠时间
	private int sleepminute = 20;
	//标记是否打开睡眠模式
	private static boolean sleepmode;
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		findViews();
		registerListeners();
		initMusicList();
		initListView();
		checkMusicfile();
		
		startService(new Intent(this, MusicService.class));
		// 绑定广播接收器，可以接收广播
		bindStatusChangedReceiver();
		sendBroadcastOnCommand(MusicService.COMMAND_CHECK_IS_PLAYING);
		// 初始化进度条的Handler
		initSeekBarHandler();
		status = MusicService.COMMAND_STOP;
		
		//默认播放模式是顺序播放
		playmode = MainActivity.MODE_LIST_SEQUENCE;
		//默认睡眠模式为关闭状态
		sleepmode = MainActivity.NOTSLEEP;
	}

	void findViews() {
		listView = (ListView) findViewById(R.id.main_listview);
		tv_current_time = (TextView) findViewById(R.id.main_tv_curtime);
		tv_total_time = (TextView) findViewById(R.id.main_tv_totaltime);
		imgBtn_Previous = (ImageButton) findViewById(R.id.main_ibtn_pre);
		imgBtn_PlayOrPause = (ImageButton) findViewById(R.id.main_ibtn_play);
		imgBtn_Previous = (ImageButton) findViewById(R.id.main_ibtn_pre);
		imgBtn_Next = (ImageButton) findViewById(R.id.main_ibtn_next);
		imgBtn_Stop = (ImageButton) findViewById(R.id.main_ibtn_stop);
		seekBar = (SeekBar) findViewById(R.id.main_seekBar);
		root_Layout = (RelativeLayout) findViewById(R.id.relativeLayout1);
		
		tv_vol = (TextView)findViewById(R.id.main_tv_volumeText);
		seekbar_vol = (SeekBar)findViewById(R.id.main_sb_volumebar);
		iv_sleep = (ImageView)findViewById(R.id.main_iv_sleep);
	}

	/** 为显示组件注册监听器 */
	private void registerListeners() {
		imgBtn_Previous.setOnClickListener(new OnClickListener() {
			public void onClick(View view) {
				sendBroadcastOnCommand(MusicService.COMMAND_PREVIOUS);
			}
		});
		imgBtn_PlayOrPause.setOnClickListener(new OnClickListener() {
			public void onClick(View view) {
				switch (status) {
				case MusicService.STATUS_PLAYING:
					sendBroadcastOnCommand(MusicService.COMMAND_PAUSE);
					break;
				case MusicService.STATUS_PAUSED:
					sendBroadcastOnCommand(MusicService.COMMAND_RESUME);
					break;
				case MusicService.COMMAND_STOP:
					sendBroadcastOnCommand(MusicService.COMMAND_PLAY);
				default:
					break;
				}
			}
		});
		imgBtn_Stop.setOnClickListener(new OnClickListener() {
			public void onClick(View view) {
				sendBroadcastOnCommand(MusicService.COMMAND_STOP);
			}
		});
		imgBtn_Next.setOnClickListener(new OnClickListener() {
			public void onClick(View view) {
				sendBroadcastOnCommand(MusicService.COMMAND_NEXT);
			}
		});
		listView.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				number = position;
				sendBroadcastOnCommand(MusicService.COMMAND_PLAY);
			}
		});
		seekBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
				if (status == MusicService.STATUS_PLAYING) {
					// 发送广播给MusicService，执行跳转
					sendBroadcastOnCommand(MusicService.COMMAND_SEEK_TO);
					// 进度条恢复移动
					seekBarHandler.sendEmptyMessageDelayed(PROGRESS_INCREASE,
							1000);
				}
			}
			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
				// 进度条暂停移动
				seekBarHandler.sendEmptyMessage(PROGRESS_PAUSE);
			}
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress,
					boolean fromUser) {
				if (status != MusicService.STATUS_STOPPED) {
					curent_time = progress;
					// 更新文本
					tv_current_time.setText(formatTime(curent_time));
				}
			}
		});
	}
	/**初始化音乐列表对象*/
	private void initMusicList() {
		musicArrayList = MusicList.getMusicList();
		//避免重复添加音乐
		if(musicArrayList.isEmpty())
		{
			Cursor mMusicCursor = this.getContentResolver().query(
					MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, null, null, null,
					MediaStore.Audio.AudioColumns.TITLE);
			int indexTitle = mMusicCursor.getColumnIndex(MediaStore.Audio.AudioColumns.TITLE);
			int indexArtist = mMusicCursor.getColumnIndex(MediaStore.Audio.AudioColumns.ARTIST);
			int indexTotalTime = mMusicCursor.getColumnIndex(MediaStore.Audio.AudioColumns.DURATION);
			int indexPath = mMusicCursor.getColumnIndex(MediaStore.Audio.AudioColumns.DATA);

			/**通过mMusicCursor游标遍历数据库，并将Music类对象加载带ArrayList中*/
			for (mMusicCursor.moveToFirst(); !mMusicCursor.isAfterLast(); mMusicCursor
					.moveToNext()) { 
				String strTitle = mMusicCursor.getString(indexTitle);
				String strArtist = mMusicCursor.getString(indexArtist);
				String strTotoalTime = mMusicCursor.getString(indexTotalTime);
				String strPath = mMusicCursor.getString(indexPath);

				if (strArtist.equals("<unknown>"))
					strArtist = "无艺术家";
				Music music = new Music(strTitle, strArtist, strPath, strTotoalTime);
				musicArrayList.add(music);
			}
		}
	}
	/**设置适配器并初始化listView*/
	private void initListView() {
		List<Map<String, String>> list_map = new ArrayList<Map<String, String>>();
		HashMap<String, String> map;
		SimpleAdapter simpleAdapter;
		for (Music music : musicArrayList) {
			map = new HashMap<String, String>();
			map.put("musicName", music.getmusicName());
			map.put("musicArtist", music.getmusicArtist());
			list_map.add(map);
		}

		String[] from = new String[] { "musicName", "musicArtist" };
		int[] to = { R.id.listview_tv_title_item, R.id.listview_tv_artist_item };

		simpleAdapter = new SimpleAdapter(this, list_map, R.layout.listview,from, to);
		listView.setAdapter(simpleAdapter);
	}
	
	/**如果列表没有歌曲，则播放按钮不可用，并提醒用户*/
	private void checkMusicfile()
	{
		if (musicArrayList.isEmpty()) {
			imgBtn_Next.setEnabled(false);
			imgBtn_PlayOrPause.setEnabled(false);
			imgBtn_Previous.setEnabled(false);
			imgBtn_Stop.setEnabled(false);
			Toast.makeText(getApplicationContext(), "当前没有歌曲文件",Toast.LENGTH_SHORT).show();
		} else {
			imgBtn_Next.setEnabled(true);
			imgBtn_PlayOrPause.setEnabled(true);
			imgBtn_Previous.setEnabled(true);
			imgBtn_Stop.setEnabled(true);
		}
	}
	/** 绑定广播接收器 */
	private void bindStatusChangedReceiver() {
		receiver = new StatusChangedReceiver();
		IntentFilter filter = new IntentFilter(
				MusicService.BROADCAST_MUSICSERVICE_UPDATE_STATUS);
		registerReceiver(receiver, filter);
	}

	private void initSeekBarHandler() {
		seekBarHandler = new Handler() {
			public void handleMessage(Message msg) {
				super.handleMessage(msg);

				switch (msg.what) {
				case PROGRESS_INCREASE:
					if (seekBar.getProgress() < total_time) {
						// 进度条前进1秒
						seekBar.incrementProgressBy(1000);
						seekBarHandler.sendEmptyMessageDelayed(
								PROGRESS_INCREASE, 1000);
						// 修改显示当前进度的文本
						tv_current_time.setText(formatTime(curent_time));
						curent_time += 1000;
					}
					break;
				case PROGRESS_PAUSE:
					seekBarHandler.removeMessages(PROGRESS_INCREASE);
					break;
				case PROGRESS_RESET:
					// 重置进度条界面
					seekBarHandler.removeMessages(PROGRESS_INCREASE);
					seekBar.setProgress(0);
					tv_current_time.setText("00:00");
					break;
				}
			}
		};
	}
	/** 发送命令，控制音乐播放。参数定义在MusicService类中 */
	private void sendBroadcastOnCommand(int command) {

		Intent intent = new Intent(MusicService.BROADCAST_MUSICSERVICE_CONTROL);
		intent.putExtra("command", command);
		// 根据不同命令，封装不同的数据
		switch (command) {
		case MusicService.COMMAND_PLAY:
			intent.putExtra("number", number);
			break;
		case MusicService.COMMAND_SEEK_TO:
			intent.putExtra("time", curent_time);
			break;
		case MusicService.COMMAND_PREVIOUS:
		case MusicService.COMMAND_NEXT:
		case MusicService.COMMAND_PAUSE:
		case MusicService.COMMAND_STOP:
		case MusicService.COMMAND_RESUME:
		default:
			break;
		}
		sendBroadcast(intent);
	}

	/** 内部类，用于播放器状态更新的接收广播 */
	class StatusChangedReceiver extends BroadcastReceiver {
		public void onReceive(Context context, Intent intent) {
			// 获取播放器状态
			status = intent.getIntExtra("status", -1);
			switch (status) {
			case MusicService.STATUS_PLAYING:
				String musicName = intent.getStringExtra("musicName");
				String musicArtist = intent.getStringExtra("musicArtist");

				seekBarHandler.removeMessages(PROGRESS_INCREASE);
				curent_time = intent.getIntExtra("time", 0);
				total_time = intent.getIntExtra("duration", 0);
				number = intent.getIntExtra("number", number);
				listView.setSelection(number);

				seekBar.setProgress(curent_time);
				seekBar.setMax(total_time);
				seekBarHandler.sendEmptyMessageDelayed(PROGRESS_INCREASE, 1000);

				tv_total_time.setText(formatTime(total_time));
				imgBtn_PlayOrPause.setBackgroundResource(R.drawable.pause);
				
				// 设置Activity的标题栏文字，提示正在播放的歌曲
				MainActivity.this.setTitle("正在播放:" + musicName + " "+ musicArtist);
				break;
			case MusicService.STATUS_PAUSED:
				seekBarHandler.sendEmptyMessage(PROGRESS_PAUSE);
				String string = MainActivity.this.getTitle().toString().replace("正在播放:", "已暂停:");
				MainActivity.this.setTitle(string);
				imgBtn_PlayOrPause.setBackgroundResource(R.drawable.play);
				break;
			case MusicService.STATUS_STOPPED:
				curent_time = 0;
				total_time = 0;
				tv_current_time.setText(formatTime(curent_time));
				tv_total_time.setText(formatTime(total_time));
				seekBarHandler.sendEmptyMessage(PROGRESS_RESET);
				MainActivity.this.setTitle("GracePlayer");
				imgBtn_PlayOrPause.setBackgroundResource(R.drawable.play);
				break;
			case MusicService.STATUS_COMPLETED:
				number = intent.getIntExtra("number", 0);
				if(playmode == MainActivity.MODE_LIST_SEQUENCE)										//顺序模式：到达列表末端时发送停止命令，否则播放下一首
				{
					if(number == MusicList.getMusicList().size()-1) 											
						sendBroadcastOnCommand(MusicService.STATUS_STOPPED);
					else
						sendBroadcastOnCommand(MusicService.COMMAND_NEXT);
				}
				else if(playmode == MainActivity.MODE_SINGLE_CYCLE)								//单曲循环
					sendBroadcastOnCommand(MusicService.COMMAND_PLAY);
				else if(playmode == MainActivity.MODE_LIST_CYCLE)										//列表循环：到达列表末端时，把要播放的音乐设置为第一首，
				{																															//					然后发送播放命令。			
					if(number == musicArrayList.size()-1)
					{
						number = 0;
						sendBroadcastOnCommand(MusicService.COMMAND_PLAY);
					}
					else sendBroadcastOnCommand(MusicService.COMMAND_NEXT);
				}
				
				seekBarHandler.sendEmptyMessage(PROGRESS_RESET);
				MainActivity.this.setTitle("GracePlayer");
				imgBtn_PlayOrPause.setBackgroundResource(R.drawable.play);
				break;
			default:
				break;
			}
		}
	}

	/** 格式化：毫秒 -> "mm:ss" */
	private String formatTime(int msec) {
		int minute = (msec / 1000) / 60;
		int second = (msec / 1000) % 60;
		String minuteString;
		String secondString;
		if (minute < 10) {
			minuteString = "0" + minute;
		} else {
			minuteString = "" + minute;
		}
		if (second < 10) {
			secondString = "0" + second;
		} else {
			secondString = "" + second;
		}
		return minuteString + ":" + secondString;
	}

	/** 设置Activity的主题，包括修改背景图片等 */
	private void setTheme(String theme) {
		if ("彩色".equals(theme)) {
			root_Layout.setBackgroundResource(R.drawable.bg_color);
		} else if ("花朵".equals(theme)) {
			root_Layout.setBackgroundResource(R.drawable.bg_digit_flower);
		} else if ("群山".equals(theme)) {
			root_Layout.setBackgroundResource(R.drawable.bg_mountain);
		} else if ("小狗".equals(theme)) {
			root_Layout.setBackgroundResource(R.drawable.bg_running_dog);
		} else if ("冰雪".equals(theme)) {
			root_Layout.setBackgroundResource(R.drawable.bg_snow);
		} else if ("女孩".equals(theme)) {
			root_Layout.setBackgroundResource(R.drawable.bg_music_girl);
		} else if ("朦胧".equals(theme)) {
			root_Layout.setBackgroundResource(R.drawable.bg_blur);
		}
	}

	@Override
	protected void onResume() {
		// TODO Auto-generated method stub
		super.onResume();
		
		// 检查播放器是否正在播放。如果正在播放，以上绑定的接收器会改变UI
		sendBroadcastOnCommand(MusicService.COMMAND_CHECK_IS_PLAYING);
		PropertyBean property = new PropertyBean(MainActivity.this);
		String theme = property.getTheme();
		// 设置Activity的主题
		setTheme(theme);
		audio_Control();
		
		//睡眠模式打开是显示图标，关闭时隐藏图标
		if(sleepmode == MainActivity.ISSLEEP) iv_sleep.setVisibility(View.VISIBLE);
		else iv_sleep.setVisibility(View.INVISIBLE);
	}

	@Override
	protected void onDestroy() {
		// TODO Auto-generated method stub
		if (status == MusicService.STATUS_STOPPED) {
			stopService(new Intent(this, MusicService.class));
		}
		super.onDestroy();
	}

	/** 创建菜单 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		
		getMenuInflater().inflate(R.menu.main, menu);
		return super.onCreateOptionsMenu(menu);
	}

	/** 处理菜单点击事件 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_theme:
			// 显示列表对话框
			new AlertDialog.Builder(this)
					.setTitle("请选择主题")
					.setItems(R.array.theme,
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int which) {
									// 获取在array.xml中定义的主题名称
									String theme = PropertyBean.THEMES[which];
									// 设置Activity的主题
									setTheme(theme);
									// 保存选择的主题
									PropertyBean property = new PropertyBean(
											MainActivity.this);
									property.setAndSaveTheme(theme);
								}
							}).show();
			break;
		case R.id.menu_about:
			// 显示文本对话框
			new AlertDialog.Builder(MainActivity.this).setTitle("GracePlayer")
					.setMessage(R.string.about).show();
			break;
		case R.id.menu_quit:
			//退出程序
			new AlertDialog.Builder(MainActivity.this).setTitle("提示")
			.setMessage(R.string.quit_message).setPositiveButton("确定", new DialogInterface.OnClickListener() {
				
				@Override
				public void onClick(DialogInterface arg0, int arg1) {
					// TODO Auto-generated method stub
					System.exit(0);
				}
			}).setNegativeButton("取消", new DialogInterface.OnClickListener() {
				
				@Override
				public void onClick(DialogInterface arg0, int arg1) {
					// TODO Auto-generated method stub
					
				}
			}).show();
			break;
		case R.id.menu_playmode:
			String[] mode = new String[] { "顺序播放", "单曲循环", "列表循环" };
			AlertDialog.Builder builder = new AlertDialog.Builder(
					MainActivity.this);
			builder.setTitle("播放模式");
			builder.setSingleChoiceItems(mode, playmode,						//设置单选项，这里第二个参数是默认选择的序号，这里根据playmode的值来确定
					new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface arg0, int arg1) {
							// TODO Auto-generated method stub
							playmode = arg1;
						}
					});
			builder.setPositiveButton("确定",
					new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface arg0, int arg1) {
							// TODO Auto-generated method stub
							switch (playmode) {
							case 0:
								playmode = MainActivity.MODE_LIST_SEQUENCE;
								Toast.makeText(getApplicationContext(), R.string.sequence, Toast.LENGTH_SHORT).show();
								break;
							case 1:
								playmode = MainActivity.MODE_SINGLE_CYCLE;
								Toast.makeText(getApplicationContext(), R.string.singlecycle, Toast.LENGTH_SHORT).show();
								break;
							case 2:
								playmode = MainActivity.MODE_LIST_CYCLE;
								Toast.makeText(getApplicationContext(), R.string.listcycle, Toast.LENGTH_SHORT).show();
								break;
							default:
								break;
							}
						}
					});
			builder.create().show(); 
			break;
		case R.id.menu_sleep:
			showSleepDialog();
			break;
		case R.id.menu_lrc:
			Intent intent = new Intent(MainActivity.this,LrcActivity.class);
			startActivity(intent);
			break;
		}
		return super.onOptionsItemSelected(item);
	}
	
	//重写onkeyDown函数
		@Override
		public boolean onKeyDown(int keyCode, KeyEvent event) {
			// TODO Auto-generated method stub
			int progress;
			switch(keyCode)
			{
			case KeyEvent.KEYCODE_BACK:
				exitByDoubleClick();
				break;
			case KeyEvent.KEYCODE_VOLUME_DOWN:
				progress = seekbar_vol.getProgress();
				if(progress != 0)
					seekbar_vol.setProgress(progress-1);
				return true;
			case KeyEvent.KEYCODE_VOLUME_UP:
				progress = seekbar_vol.getProgress();
				if(progress != seekbar_vol.getMax())
					seekbar_vol.setProgress(progress+1);
				return true;
			default:
					break;
			}
			return false;
		}
	private void exitByDoubleClick()
	{
		Timer timer = null;
		if(isExit == false)
		{
			isExit = true;		//准备退出
			Toast.makeText(this, "再按一次退出程序！", Toast.LENGTH_SHORT).show();
			timer = new Timer();
			timer.schedule(new TimerTask() {
				
				@Override
				public void run() {
					// TODO Auto-generated method stub
					isExit = false;
				}
			}, 2000);   //2 秒后会执行 run函数的内容，如果2秒内没有按下返回键，则启动定时器修改isExit的值
		}
		else
		{
			System.exit(0);
		}
	}
	private void audio_Control()
	{
		//获取音量管理器
			final AudioManager audioManager = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
			//设置当前调整音量大小只是针对媒体音乐
			this.setVolumeControlStream(AudioManager.STREAM_MUSIC);
			//设置滑动条最大值
			final int max_progress = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
			seekbar_vol.setMax(max_progress); 
			//获取当前音量
			int progress = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
			seekbar_vol.setProgress(progress);
			
			tv_vol .setText("音量： "+(progress*100/max_progress)+"%"); 
			
			seekbar_vol.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
				@Override
				public void onStopTrackingTouch(SeekBar arg0) {
					// TODO Auto-generated method stub
				}
				@Override
				public void onStartTrackingTouch(SeekBar arg0) {
					// TODO Auto-generated method stub
				}
				@Override
				public void onProgressChanged(SeekBar arg0, int arg1, boolean arg2) {
					// TODO Auto-generated method stub
					tv_vol .setText("音量： "+(arg1*100)/(max_progress)+"%");
					audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, arg1, AudioManager.FLAG_PLAY_SOUND);
				}
			});
	}
	private void showSleepDialog()   
	{
		//先用getLayoutInflater().inflate方法获取布局，用来初始化一个View类对象
		final View userview = this.getLayoutInflater().inflate(R.layout.dialog, null);
		
	    //通过View类的findViewById方法获取到组件对象
		final TextView tv_minute = (TextView)userview.findViewById(R.id.dialog_tv);
		final Switch switch1 = (Switch)userview.findViewById(R.id.dialog_switch);
		final SeekBar seekbar = (SeekBar)userview.findViewById(R.id.dialog_seekbar);
		
		tv_minute.setText("睡眠于:"+sleepminute+"分钟");
		//根据当前的睡眠状态来确定Switch的状态
		if(sleepmode == MainActivity.ISSLEEP) switch1.setChecked(true);
		seekbar.setMax(60);
		seekbar.setProgress(sleepminute);
		seekbar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
			
			@Override
			public void onStopTrackingTouch(SeekBar arg0) {
				// TODO Auto-generated method stub
			
			}
			@Override
			public void onStartTrackingTouch(SeekBar arg0) {
				// TODO Auto-generated method stub
			}
			@Override
			public void onProgressChanged(SeekBar arg0, int arg1, boolean arg2) {
				// TODO Auto-generated method stub
				sleepminute = arg1;
				tv_minute.setText("睡眠于:"+sleepminute+"分钟");
				
			}
		});
		switch1.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			
			@Override
			public void onCheckedChanged(CompoundButton arg0, boolean arg1) {
				// TODO Auto-generated method stub
				sleepmode = arg1;
			}
		});
		//定义定时器任务
		final TimerTask timerTask = new TimerTask() {
			@Override
			public void run() {
				// TODO Auto-generated method stub
				System.exit(0);
			}
		};
		//定义对话框以及初始化
		final AlertDialog.Builder dialog = new AlertDialog.Builder(this);
		dialog.setTitle("选择睡眠时间(0~60分钟)");
		//设置布局
		dialog.setView(userview);
		//设置取消按钮响应事件
		dialog.setNegativeButton("取消", new DialogInterface.OnClickListener() {
			
			@Override
			public void onClick(DialogInterface arg0, int arg1) {
				// TODO Auto-generated method stub
				arg0.dismiss();
			}
		});
		//设置重置按钮响应时间
		dialog.setNeutralButton("重置", new DialogInterface.OnClickListener() {
			
			@Override
			public void onClick(DialogInterface arg0, int arg1) {
				// TODO Auto-generated method stub
				if(sleepmode == MainActivity.ISSLEEP)
				{
					timerTask.cancel();
					timer_sleep.cancel();
				}
				sleepmode = MainActivity.NOTSLEEP;
				sleepminute = 20;
				iv_sleep.setVisibility(View.INVISIBLE);
			}
		});
		//设置确定按钮响应事件
		dialog.setPositiveButton("确定", new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface arg0, int arg1) {
				// TODO Auto-generated method stub
				if(sleepmode == MainActivity.ISSLEEP)
				{
					timer_sleep = new Timer();
					int time =seekbar.getProgress();
					//启动任务，time*60*1000毫秒后执行
					timer_sleep.schedule(timerTask, time*60*1000);
					iv_sleep.setVisibility(View.VISIBLE);
				}
				else
				{
					//取消任务
					timerTask.cancel();
					if(timer_sleep != null) timer_sleep.cancel();
					arg0.dismiss();
					iv_sleep.setVisibility(View.INVISIBLE);
				}
			}
		});
		
		dialog.show();
	}
}
