package com.builtbydeep.sovra;

import android.Manifest;
import android.animation.*;
import android.app.*;
import android.app.Activity;
import android.content.*;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.media.*;
import android.net.*;
import android.os.*;
import android.text.*;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.style.*;
import android.util.*;
import android.view.*;
import android.view.View;
import android.view.View.*;
import android.view.animation.*;
import android.webkit.*;
import android.widget.*;
import androidx.annotation.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import com.builtbydeep.sovra.databinding.*;
import com.google.firebase.FirebaseApp;
import com.k2fsa.sherpa.onnx.*;
import com.tom_roush.pdfbox.*;
import java.io.*;
import java.text.*;
import java.util.*;
import java.util.regex.*;
import org.json.*;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.android.material.snackbar.Snackbar;
import android.media.AudioManager;
import android.media.AudioFocusRequest;
import android.os.Build;
import android.content.Context;
import com.builtbydeep.sovra.system.VoxMediaController;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.concurrent.LinkedBlockingQueue;
import com.builtbydeep.sovra.system.VoxMediaService;
import android.os.Process;


public class GenerateFragmentActivity extends Fragment {
	private static final String[] SUPERTONIC_VOICE_PROFILES = {
		"F1 - Calm, Clear",
		"F2 - Bright, Professional",
		"F3 - Soft, Expressive",
		"F4 - Energetic, Versatile",
		"F5 - Airy, Warm",
		"M1 - Lively, Upbeat",
		"M2 - Warm, Narrative",
		"M3 - Deep, Authoritative",
		"M4 - Measured, Resonant",
		"M5 - Dynamic, Dramatic"
	};

	
	public final int REQ_CD_FILEPICKER = 101;
	
	private GenerateFragmentBinding binding;
	private boolean isAudioGeneratedForCurrentText = false;
	private String lastGeneratedText = "";
	private byte[] lastGeneratedPcmData = null;
	private byte[] lastGeneratedMp3Data = null;
	private volatile java.io.File lastGeneratedPcmFile;
	private volatile java.io.File activeEdgePcmFile;
	private volatile long lastGeneratedPcmBytes;
	private android.media.AudioTrack audioTrack;
	private android.animation.ValueAnimator playheadAnimator;
	androidx.appcompat.widget.ListPopupWindow listPopupWindow;
	android.widget.ArrayAdapter<String> voiceAdapter;
	private int lastGeneratedSampleRate = 22050;
	private volatile boolean isCancelled = false;
	private volatile boolean isGenerating = false;
	com.builtbydeep.sovra.GenerationParams lastParams = null;
	private volatile int currentGenerationId = 0;
	private String currentUiModelPath = "";
	private volatile android.media.AudioTrack liveStreamTrack = null;
	private volatile boolean edgeStreamStarted;
	private volatile boolean isEdgeTtsGenerating;
	private volatile long edgePlaybackId;
	private String lastSettingsState = "";
	private java.util.ArrayList<com.builtbydeep.sovra.KokoroVoiceHelper.RegionItem> kokoroRegions =
			new java.util.ArrayList<>();
	private java.util.ArrayList<com.builtbydeep.sovra.KokoroVoiceHelper.VoiceItem> kokoroFilteredVoices =
			new java.util.ArrayList<>();
	private boolean suppressKokoroFilterCallbacks;
	private volatile com.builtbydeep.sovra.EdgeTtsClient edgeTtsClient =
			new com.builtbydeep.sovra.EdgeTtsClient();
	private volatile java.util.List<com.builtbydeep.sovra.EdgeTtsClient.Voice> edgeVoices =
			com.builtbydeep.sovra.EdgeTtsClient.fallbackVoices();
	private boolean suppressEdgeFilterCallbacks;
	private boolean edgeVoicesFetched;
	private volatile java.util.List<com.builtbydeep.sovra.CloudTtsProvider.Voice> cloudVoices =
			new java.util.ArrayList<>();
	private volatile boolean cloudVoicesFetched;
	private String cloudVoiceCacheKey = "";
	private boolean suppressCloudFilterCallbacks;
	android.media.AudioManager audioManager;
	android.media.AudioManager.OnAudioFocusChangeListener focusListener;
	android.media.AudioFocusRequest focusRequest;
	
	private SharedPreferences sp1;
	private SharedPreferences sp2;
	private SharedPreferences sp3;
	private Intent FilePicker = new Intent(Intent.ACTION_GET_CONTENT);
	private SharedPreferences spHistory;
	
	@NonNull
	@Override
	public View onCreateView(@NonNull LayoutInflater _inflater, @Nullable ViewGroup _container, @Nullable Bundle _savedInstanceState) {
		binding = GenerateFragmentBinding.inflate(_inflater, _container, false);
		initialize(_savedInstanceState, binding.getRoot());
		FirebaseApp.initializeApp(getContext());
		initializeLogic();
		return binding.getRoot();
	}
	
	private void initialize(Bundle _savedInstanceState, View _view) {
		sp1 = getContext().getSharedPreferences("sp1", Activity.MODE_PRIVATE);
		sp2 = getContext().getSharedPreferences("sp2", Activity.MODE_PRIVATE);
		sp3 = getContext().getSharedPreferences("sp3", Activity.MODE_PRIVATE);
		FilePicker.setType("*/*");
		FilePicker.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
		spHistory = getContext().getSharedPreferences("spHistory", Activity.MODE_PRIVATE);
		binding.supertonicVoiceButton.setOnClickListener(view -> showSupertonicVoiceSheet());
		binding.supertonicSettingsButton.setOnClickListener(view -> showSupertonicQualityDialog());

		binding.history.setOnClickListener(view -> {
			if (getActivity() != null) {
				androidx.viewpager2.widget.ViewPager2 viewPager =
						getActivity().findViewById(R.id.viewpager);
				if (viewPager != null) viewPager.setCurrentItem(2, false);
			}
		});
		binding.dictionary.setOnClickListener(view -> {
			startActivity(new Intent(requireContext(), EpubReaderActivity.class));
		});
		binding.cardAdd.setOnClickListener(view -> {
			Intent documentIntent = new Intent(Intent.ACTION_GET_CONTENT);
			documentIntent.setType("*/*");
			documentIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
			try {
				startActivityForResult(Intent.createChooser(documentIntent, "Import text or document"), REQ_CD_FILEPICKER);
			} catch (android.content.ActivityNotFoundException error) {
				com.google.android.material.snackbar.Snackbar.make(binding.getRoot(),
						"No file picker is available.", com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show();
			}
		});
		binding.btnVoiceInput.setOnClickListener(view -> {
			Intent speechIntent = new Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
			speechIntent.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
					android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
			speechIntent.putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Speak text to synthesize");
			try {
				startActivityForResult(speechIntent, 100);
			} catch (android.content.ActivityNotFoundException error) {
				com.google.android.material.snackbar.Snackbar.make(binding.getRoot(),
						"Voice typing is not available on this device.", com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show();
			}
		});
		binding.btnPaste.setOnClickListener(view -> {
			android.content.ClipboardManager clipboard =
					(android.content.ClipboardManager) requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE);
			if (clipboard == null || !clipboard.hasPrimaryClip() || clipboard.getPrimaryClip() == null
					|| clipboard.getPrimaryClip().getItemCount() == 0) {
				com.google.android.material.snackbar.Snackbar.make(binding.getRoot(),
						"Clipboard is empty.", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT).show();
				return;
			}
			CharSequence pastedText = clipboard.getPrimaryClip().getItemAt(0).coerceToText(requireContext());
			if (pastedText == null || pastedText.length() == 0) return;
			int start = Math.max(0, binding.etInput.getSelectionStart());
			int end = Math.max(0, binding.etInput.getSelectionEnd());
			if (start > end) {
				int swap = start;
				start = end;
				end = swap;
			}
			android.text.Editable text = binding.etInput.getText();
			text.replace(start, end, pastedText);
			int cursor = Math.min(start + pastedText.length(), text.length());
			binding.etInput.setSelection(cursor);
		});
		binding.btnClearText.setOnClickListener(view -> binding.etInput.setText(""));
		
		binding.btnGenerate.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View _view) {
				String inputText = binding.etInput.getText().toString().trim();
				
				if (inputText.isEmpty()) {
					if (getView() != null) {
						com.google.android.material.snackbar.Snackbar.make(getView(), "Please enter some text first.", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
						.setBackgroundTint(android.graphics.Color.parseColor("#EF4444")).setTextColor(android.graphics.Color.WHITE).show();
					}
					return;
				}
				
				String currentOnnxModel = sp1.getString("active_model", "");
				String currentTokens    = sp1.getString("active_tokens", "");
				String currentModelType = sp1.getString("active_model_type", "vits");
				String currentVoicesBin = sp1.getString("active_voices_bin", "");
				boolean isEdgeTts = com.builtbydeep.sovra.EdgeTtsClient.MODEL_TYPE
						.equalsIgnoreCase(currentModelType);
				String cloudProviderId = cloudProviderFromType(currentModelType);
				boolean isCloudTts = !cloudProviderId.isEmpty();
				if (com.builtbydeep.sovra.CloudTtsClients.WITAI.equals(cloudProviderId)
						&& com.builtbydeep.sovra.WitAiTtsProvider.containsDevanagari(inputText)) {
					android.widget.Toast.makeText(requireContext(),
							"Wit.ai supports English voices only.", android.widget.Toast.LENGTH_SHORT).show();
					return;
				}
				
				boolean isPunctOn   = sp3.getBoolean("smart_punct", false);
				boolean isEmotionOn = sp3.getBoolean("emotion_tags", false);
				boolean isMultiSpeakerOn = sp3.getBoolean("multi_speaker_tags", false);
				float currentSpeed  = sp3.getFloat("voice_speed", 1.0f);
				float currentPitch  = sp3.getFloat("voice_pitch", 1.0f);
				float currentSilence = sp3.getFloat("silence_scale", 0.2f);
				int currentVoiceStyle = sp3.getInt("voice_style", 1); 
				int currentKokoroVoiceId = sp1.getInt("active_kokoro_speaker", 31);
				final int currentPiperVoiceId = sp1.getInt("active_piper_speaker", 0); 
				
				if (!isEdgeTts && !isCloudTts && (currentOnnxModel.isEmpty() || currentTokens.isEmpty())) {
					if (getView() != null) {
						com.google.android.material.snackbar.Snackbar.make(getView(), "Please select a Voice Model from Models tab.", com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
						.setBackgroundTint(android.graphics.Color.parseColor("#EF4444")).setTextColor(android.graphics.Color.WHITE).show();
					}
					return;
				}
				if ((isEdgeTts || isCloudTts) && !isNetworkAvailable()) {
					if (getView() != null) {
						com.google.android.material.snackbar.Snackbar.make(getView(),
								isEdgeTts ? "Microsoft Edge TTS needs an internet connection."
										: "Cloud TTS needs an internet connection.",
								com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
								.setBackgroundTint(android.graphics.Color.parseColor("#EF4444"))
								.setTextColor(android.graphics.Color.WHITE).show();
					}
					return;
				}
				if (isCloudTts && !com.builtbydeep.sovra.ExtendedCloudProviders
						.isKeyless(cloudProviderId) && sp1.getString(
						com.builtbydeep.sovra.CloudTtsClients.getApiKeyPreference(cloudProviderId), "")
						.trim().isEmpty()) {
					new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
							.setTitle("API key required")
							.setMessage("Add your " + sp1.getString("active_model_name", "cloud provider")
									+ " API key in Settings before generating audio.")
							.setPositiveButton("Open Settings", (dialog, which) -> {
								androidx.viewpager2.widget.ViewPager2 pager =
										requireActivity().findViewById(R.id.viewpager);
								if (pager != null) pager.setCurrentItem(3, false);
							})
							.setNegativeButton("Cancel", null).show();
					return;
				}
				
				boolean isVoiceParamChanged = false;
				if (lastParams == null ||
				!currentOnnxModel.equals(lastParams.onnxModel) ||
				isEdgeTts && !sp1.getString("edge_voice", "hi-IN-SwaraNeural")
						.equals(sp3.getString("last_edge_voice", "")) ||
				isCloudTts && (!sp1.getString("cloud_voice_" + cloudProviderId, "")
						.equals(sp3.getString("last_cloud_voice", ""))
						|| !sp1.getString(com.builtbydeep.sovra.CloudTtsClients
								.getSelectedModelPreference(cloudProviderId),
								com.builtbydeep.sovra.CloudTtsClients.defaultModel(cloudProviderId))
								.equals(sp3.getString("last_cloud_model", ""))) ||
				currentKokoroVoiceId != lastParams.kokoroVoiceId ||
				currentPiperVoiceId != sp3.getInt("last_piper_speaker", -1) || 
				currentSpeed != lastParams.speed ||
				currentPitch != lastParams.pitch ||
				isPunctOn != lastParams.punctOn ||
				isEmotionOn != lastParams.emotionOn ||
				isMultiSpeakerOn != sp3.getBoolean("last_multi_speaker_state", false) ||
				currentSilence != sp3.getFloat("last_silence_scale", -1f) ||
				currentVoiceStyle != sp3.getInt("last_voice_style", -1)) { 
					
					isVoiceParamChanged = true;
					sp3.edit()
					.putBoolean("last_multi_speaker_state", isMultiSpeakerOn)
					.putFloat("last_silence_scale", currentSilence)
					.putInt("last_voice_style", currentVoiceStyle) 
					.putInt("last_piper_speaker", currentPiperVoiceId) 
					.apply();
				}
				
				if (isGenerating) {
					if (isEdgeTts && edgeStreamStarted) {
						_cancelGeneration();
						return;
					}
					if (!isCancelled) {
						new com.google.android.material.dialog.MaterialAlertDialogBuilder(getContext())
						.setTitle("Cancel Generation")
						.setMessage("Are you sure you want to cancel the voice synthesis?")
						.setPositiveButton("Yes, Cancel", (dialog, which) -> {
							_cancelGeneration();
							dialog.dismiss();
						})
						.setNegativeButton("No", (dialog, which) -> dialog.dismiss())
						.show();
					}
					return;
				}
				
				if (isAudioGeneratedForCurrentText && !isVoiceParamChanged) {
					_toggleGeneratePlayback();
				} else {
					final com.builtbydeep.sovra.GenerationParams params = new com.builtbydeep.sovra.GenerationParams(
					inputText, currentOnnxModel, currentTokens, currentModelType, currentVoicesBin,
					currentKokoroVoiceId, currentSpeed, currentPitch, isPunctOn, isEmotionOn
					);
					final String selectedEdgeVoice = sp1.getString("edge_voice", "hi-IN-SwaraNeural");
					final String selectedEdgeLocale = sp1.getString("edge_locale", "hi-IN");
					final String selectedCloudProvider = cloudProviderId;
					final String selectedCloudModel = isCloudTts
							? sp1.getString(com.builtbydeep.sovra.CloudTtsClients
									.getSelectedModelPreference(cloudProviderId),
									com.builtbydeep.sovra.CloudTtsClients.defaultModel(cloudProviderId))
							: "";
					final String selectedCloudApiKey = isCloudTts
							? sp1.getString(com.builtbydeep.sovra.CloudTtsClients
									.getApiKeyPreference(cloudProviderId), "").trim() : "";
					final String selectedSupertonicLanguage = com.builtbydeep.sovra.SupertonicEngine.normalizeLanguage(
							sp1.getString("active_language", "en"));
					final int selectedSupertonicSteps = Math.max(
							SupertonicEngine.MIN_GENERATION_STEPS,
							Math.min(SupertonicEngine.MAX_GENERATION_STEPS,
									sp3.getInt("supertonic_num_steps", SupertonicEngine.DEFAULT_GENERATION_STEPS)));
					
					_forceResetToIdle();
					final com.builtbydeep.sovra.EdgeTtsClient generationEdgeTtsClient =
							new com.builtbydeep.sovra.EdgeTtsClient();
					edgeTtsClient = generationEdgeTtsClient;
					isGenerating = true;
					isCancelled = false;
					if (com.builtbydeep.sovra.CloudTtsClients.WITAI.equals(selectedCloudProvider)) {
						com.builtbydeep.sovra.CloudTtsClients.getWitAiProvider()
								.cancelAndClean(requireContext().getApplicationContext());
					}
					isEdgeTtsGenerating = isEdgeTts;
					edgeStreamStarted = false;
					final int myGenId = currentGenerationId;
					final long myEdgePlaybackId = ++edgePlaybackId;
					
					binding.btnGenerate.setEnabled(true);
					binding.btnGenerate.setAlpha(1.0f);
					binding.imageview65.setVisibility(android.view.View.GONE);
					binding.progressGenerating.setVisibility(android.view.View.VISIBLE);
					binding.textview69.setTextColor(android.graphics.Color.parseColor("#4A9EFF"));
					binding.textview92.setText("Cancel");
					binding.imageview52.setImageResource(R.drawable.ic_close);
					
					final boolean isKokoro = _isKokoroModel(params.modelType);
					final boolean isSupertonic = params.modelType.equals("supertonic");
					final boolean isMmsModel = params.modelType.equals("mms");
					final boolean isCloud = !selectedCloudProvider.isEmpty();
					final android.content.Context generationContext = requireContext().getApplicationContext();
					
					java.util.List<String> sentences = new java.util.ArrayList<>();
					String[] parts = params.text.split("(?<=[.!?\\n\u0964])\\s+");
					for (String part : parts) {
						if (!part.trim().isEmpty()) sentences.add(part.trim());
					}
					final int totalSentences = sentences.size();
					if (totalSentences == 0) sentences.add(params.text);
					
					binding.textview69.setText("GENERATING VOICE... 0/" + totalSentences);
					
					com.builtbydeep.sovra.system.VoxMediaController.getInstance(getContext()).updatePlaybackState("Sovra Audio", "Generating...", com.builtbydeep.sovra.system.VoxMediaController.STATE_GENERATING, false);
					
					new Thread(() -> {
						android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
						
						String loadResult = "";
						byte[] finalGeneratedPcm = null;
						byte[] finalGeneratedMp3 = null;
						java.io.File generatedPcmFile = null;
						long generatedPcmBytes = 0;
						int generatedSampleRate = 24000;
						java.io.ByteArrayOutputStream pcmStream = new java.io.ByteArrayOutputStream();
						
						float noise = 0.667f;
						float noiseW = 0.80f;
						
						if (currentVoiceStyle == 0) { 
							noise = 0.33f;
							noiseW = 0.50f;
						} else if (currentVoiceStyle == 2) { 
							noise = 0.85f;
							noiseW = 0.90f;
						}
						
						if (isEdgeTts) {
							try {
								com.builtbydeep.sovra.EdgeTtsClient.Voice voice =
										new com.builtbydeep.sovra.EdgeTtsClient.Voice(
												selectedEdgeVoice, selectedEdgeLocale,
												sp1.getString("edge_voice_gender", "Unknown"));
								generatedPcmFile = java.io.File.createTempFile(
										"edge-tts-", ".pcm", generationContext.getCacheDir());
								activeEdgePcmFile = generatedPcmFile;
								final int[] streamSampleRate = {0};
								final boolean[] firstAudioChunk = {true};
								try (java.io.BufferedOutputStream pcmOutput =
											 new java.io.BufferedOutputStream(
													 new java.io.FileOutputStream(generatedPcmFile))) {
									com.builtbydeep.sovra.EdgeTtsClient.SynthesisResult result =
											generationEdgeTtsClient.synthesizeStreaming(
													generationContext, params.text,
													voice, params.speed, params.pitch,
													(pcm, sampleRate) -> {
														if (isCancelled || myGenId != currentGenerationId) {
															throw new java.io.IOException(
																	"Microsoft Edge TTS synthesis was cancelled.");
														}
														if (streamSampleRate[0] == 0) {
															streamSampleRate[0] = sampleRate;
														} else if (streamSampleRate[0] != sampleRate) {
															throw new java.io.IOException(
																	"Microsoft Edge TTS changed its sample rate.");
														}
														pcmOutput.write(pcm);
														android.media.AudioTrack track = liveStreamTrack;
														if (track == null) {
															track = createEdgeStreamTrack(sampleRate);
															liveStreamTrack = track;
															if (audioManager != null && focusRequest != null) {
																audioManager.requestAudioFocus(focusRequest);
															}
															track.play();
															edgeStreamStarted = true;
														}
														int offset = 0;
														while (offset < pcm.length) {
															if (isCancelled || myGenId != currentGenerationId
																	|| liveStreamTrack != track) {
																throw new java.io.IOException(
																		"Microsoft Edge TTS playback was cancelled.");
															}
															int written = track.write(
																	pcm, offset, pcm.length - offset);
															if (written < 0) {
																throw new java.io.IOException(
																		"Unable to write Edge TTS audio to playback.");
															}
															if (written == 0) {
																try {
																	Thread.sleep(10L);
																} catch (InterruptedException interrupted) {
																	Thread.currentThread().interrupt();
																	throw new java.io.IOException(
																			"Edge TTS playback was interrupted.",
																			interrupted);
																}
															} else {
																offset += written;
															}
														}
														if (firstAudioChunk[0]) {
															firstAudioChunk[0] = false;
															if (getActivity() != null && isAdded()) {
																getActivity().runOnUiThread(() -> {
																	if (myGenId != currentGenerationId
																			|| isCancelled) return;
																	binding.imageview52.setImageResource(
																			R.drawable.icon_pause_circle);
																	binding.textview92.setText("Pause");
																	binding.textview69.setText(
																			"STREAMING EDGE TTS...");
																	com.builtbydeep.sovra.system.VoxMediaController
																			.getInstance(getContext())
																			.updatePlaybackState("Sovra Audio",
																					"Playing...", com.builtbydeep.sovra
																							.system.VoxMediaController.STATE_PLAYING,
																					false);
																});
															}
														}
													}, () -> !isCancelled
															&& myGenId == currentGenerationId);
									generatedSampleRate = result.sampleRate;
									generatedPcmBytes = result.pcmBytes;
								}
								loadResult = "Success";
							} catch (Exception error) {
								loadResult = error.getMessage() == null
										? "Microsoft Edge TTS synthesis failed." : error.getMessage();
								android.util.Log.e("GenerateFragmentActivity", loadResult, error);
								if (generatedPcmFile != null) generatedPcmFile.delete();
								if (activeEdgePcmFile == generatedPcmFile) activeEdgePcmFile = null;
								generatedPcmFile = null;
								if (myGenId == currentGenerationId && liveStreamTrack != null) {
									try {
										liveStreamTrack.pause();
										liveStreamTrack.flush();
										liveStreamTrack.stop();
										liveStreamTrack.release();
									} catch (Exception releaseError) {
										android.util.Log.w("GenerateFragmentActivity",
												"Unable to release failed Edge TTS playback.",
												releaseError);
									}
									liveStreamTrack = null;
									edgeStreamStarted = false;
								}
							}
						} else if (isCloud) {
							try {
								if (isCancelled || myGenId != currentGenerationId) {
									throw new java.io.IOException("Cloud TTS synthesis was cancelled.");
								}
								com.builtbydeep.sovra.CloudTtsProvider provider =
										com.builtbydeep.sovra.CloudTtsClients.get(selectedCloudProvider);
								java.util.List<com.builtbydeep.sovra.CloudTtsProvider.Voice> fetched =
										com.builtbydeep.sovra.CloudTtsClients.filterVoicesForModel(
												selectedCloudProvider, selectedCloudModel, cloudVoices);
								if (fetched.isEmpty()) {
									fetched = com.builtbydeep.sovra.CloudTtsClients.filterVoicesForModel(
											selectedCloudProvider, selectedCloudModel,
											provider.fetchVoices(selectedCloudApiKey));
								}
								String savedVoice = sp1.getString("cloud_voice_" + selectedCloudProvider, "");
								com.builtbydeep.sovra.CloudTtsProvider.Voice selectedVoice = null;
								for (com.builtbydeep.sovra.CloudTtsProvider.Voice voice : fetched) {
									if (voice.id.equals(savedVoice)) {
										selectedVoice = voice;
										break;
									}
								}
								if (selectedVoice == null && !fetched.isEmpty()) selectedVoice = fetched.get(0);
								if (selectedVoice == null) {
									throw new java.io.IOException("No compatible voices are available for "
											+ com.builtbydeep.sovra.CloudTtsClients.modelLabel(
													selectedCloudProvider, selectedCloudModel) + ".");
								}
								sp1.edit().putString("cloud_voice_" + selectedCloudProvider, selectedVoice.id)
										.putString("cloud_locale_" + selectedCloudProvider, selectedVoice.locale)
										.putString("cloud_region_" + selectedCloudProvider, selectedVoice.region)
										.apply();
								java.util.List<com.builtbydeep.sovra.CloudStudioAdapter.Segment> segments =
										com.builtbydeep.sovra.CloudStudioAdapter.prepare(
												selectedCloudProvider, selectedCloudModel, params.text,
												selectedVoice, fetched, sp3);
								com.builtbydeep.sovra.CloudStudioAdapter.Synthesis synthesis =
										com.builtbydeep.sovra.CloudStudioAdapter.synthesize(
												generationContext, selectedCloudProvider, selectedCloudModel,
												selectedCloudApiKey, segments, isPunctOn, currentSilence, sp1,
												() -> !isCancelled && myGenId == currentGenerationId);
								finalGeneratedPcm = synthesis.pcm;
								finalGeneratedMp3 = synthesis.mp3;
								generatedSampleRate = synthesis.sampleRate;
								loadResult = "Success";
							} catch (Exception error) {
								loadResult = error.getMessage() == null
										? "Cloud TTS synthesis failed." : error.getMessage();
								android.util.Log.e("GenerateFragmentActivity", loadResult, error);
							}
						} else if (isKokoro) {
							com.builtbydeep.sovra.KokoroEngine.getInstance().setActiveSpeakerId(params.kokoroVoiceId);
							loadResult = com.builtbydeep.sovra.KokoroEngine.getInstance().loadModel(
									getContext(), params.onnxModel, params.tokens, params.voicesBin);
							if ("Success".equals(loadResult)) generatedSampleRate = com.builtbydeep.sovra.KokoroEngine.getInstance().getSampleRate();
						} else if (isSupertonic) {
							com.builtbydeep.sovra.SupertonicEngine engine = com.builtbydeep.sovra.SupertonicEngine.getInstance();
							engine.setActiveSpeakerId(sp1.getInt("active_supertonic_speaker", 0));
							String modelDir = sp1.getString("active_supertonic_dir", "");
							engine.setActiveLanguage(selectedSupertonicLanguage);
							loadResult = engine.loadModel(getContext(), modelDir, selectedSupertonicLanguage);
							if ("Success".equals(loadResult)) generatedSampleRate = engine.getSampleRate();
						} else {
							com.builtbydeep.sovra.VoiceEngine.getInstance().setActiveSpeakerId(currentPiperVoiceId); 
							com.builtbydeep.sovra.VoiceEngine.getInstance().setNoiseScale(noise);
							com.builtbydeep.sovra.VoiceEngine.getInstance().setNoiseScaleW(noiseW);
							
							String activeLanguage = sp1.getString("active_language", "");
							loadResult = com.builtbydeep.sovra.VoiceEngine.getInstance().loadModel(getContext(), params.onnxModel, params.tokens, activeLanguage, isMmsModel);
							if ("Success".equals(loadResult)) generatedSampleRate = com.builtbydeep.sovra.VoiceEngine.getInstance().getSampleRate();
						}
						
						if (!isEdgeTts && generatedSampleRate <= 0) {
							if (isKokoro) generatedSampleRate = 24000;
							else if (isSupertonic) generatedSampleRate = 44100;
							else generatedSampleRate = 22050;
						}
						
						final boolean[] doHandoff = {false};
						final int[] handoffHeadPos = {0};
						final int[] totalFramesWritten = {0};
						
						if ("Success".equals(loadResult) && !isEdgeTts && !isCloud) {
							int minBufferSize = android.media.AudioTrack.getMinBufferSize(generatedSampleRate, 
							android.media.AudioFormat.CHANNEL_OUT_MONO, 
							android.media.AudioFormat.ENCODING_PCM_16BIT);
							
							if (minBufferSize <= 0) {
								minBufferSize = generatedSampleRate * 2; 
							}
							
							liveStreamTrack = new android.media.AudioTrack.Builder()
							.setAudioAttributes(new android.media.AudioAttributes.Builder()
							.setUsage(android.media.AudioAttributes.USAGE_MEDIA)
							.setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
							.build())
							.setAudioFormat(new android.media.AudioFormat.Builder()
							.setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
							.setSampleRate(generatedSampleRate)
							.setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO)
							.build())
							.setBufferSizeInBytes(minBufferSize)
							.setTransferMode(android.media.AudioTrack.MODE_STREAM)
							.build();
							
							java.util.concurrent.LinkedBlockingQueue<byte[]> audioQueue = new java.util.concurrent.LinkedBlockingQueue<>();
							
							Thread playerThread = new Thread(() -> {
								android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
								try {
									if (focusRequest != null && audioManager != null) {
										audioManager.requestAudioFocus(focusRequest);
									}
									
									if (liveStreamTrack != null) liveStreamTrack.play();
									
									while (true) {
										if (isCancelled || myGenId != currentGenerationId) break;
										
										if (doHandoff[0]) break; 
										
										byte[] chunk = audioQueue.take();
										if (chunk.length == 0) break; 
										
										while (liveStreamTrack != null && liveStreamTrack.getPlayState() != android.media.AudioTrack.PLAYSTATE_PLAYING && !isCancelled) {
											Thread.sleep(50); 
										}
										
										if (liveStreamTrack != null && !isCancelled) {
											int written = liveStreamTrack.write(chunk, 0, chunk.length);
											if (written > 0) totalFramesWritten[0] += (written / 2); 
										}
									}
								} catch (InterruptedException e) {
									Thread.currentThread().interrupt();
								} catch (Exception ignored) {
								} finally {
									try {
										if (liveStreamTrack != null) {
											handoffHeadPos[0] = liveStreamTrack.getPlaybackHeadPosition();
											liveStreamTrack.stop();
											liveStreamTrack.release();
											liveStreamTrack = null;
										}
									} catch (Exception ignored) {}
									
									try {
										if (audioManager != null && focusRequest != null) {
											audioManager.abandonAudioFocusRequest(focusRequest);
										}
									} catch (Exception ignored) {}
								}
							});
							playerThread.start();
							
							int doneCount = 0;
							for (String sentence : sentences) {
								if (isCancelled || myGenId != currentGenerationId) break;
								
								if (isMultiSpeakerOn) {
									sentence = com.builtbydeep.sovra.PiperVoiceHelper.processAndSetSpeaker(sentence, isKokoro, isSupertonic);
								}
								
								if (sentence.isEmpty()) {
									doneCount++;
									continue;
								}
								
								byte[] chunkData = null;
								
								if ((params.punctOn || params.emotionOn) && !isSupertonic) {
									chunkData = com.builtbydeep.sovra.AudioEmotionHelper.processAndGenerate(sentence, params.punctOn, params.emotionOn, params.speed, params.pitch, 1.0f, currentSilence);
								} else {
									if (isKokoro) {
										chunkData = com.builtbydeep.sovra.KokoroEngine.getInstance().generateAudioPCM(sentence, params.speed, params.pitch);
									} else if (isSupertonic) {
										chunkData = com.builtbydeep.sovra.SupertonicEngine.getInstance().generateAudioPCM(
												sentence, params.speed, params.pitch, selectedSupertonicLanguage,
												selectedSupertonicSteps);
									} else {
										chunkData = com.builtbydeep.sovra.VoiceEngine.getInstance().generateAudioPCM(sentence, params.speed, params.pitch);
									}
								}
								
								if (isCancelled || myGenId != currentGenerationId) break;
								
								if (chunkData != null && chunkData.length > 0) {
									try {
										pcmStream.write(chunkData);
										audioQueue.put(chunkData);
									} catch (Exception ignored) {}
								}
								doneCount++;
								final int current = doneCount;
								
								if (getActivity() != null && isAdded()) {
									getActivity().runOnUiThread(() -> {
										if (!isCancelled && myGenId == currentGenerationId) {
											binding.textview69.setText("GENERATING... " + current + "/" + totalSentences);
										}
									});
								}
							}
							
							doHandoff[0] = true;
							try {
								audioQueue.put(new byte[0]);
								playerThread.join(3000); 
							} catch (Exception ignored) {}
						}
						
						if (!isEdgeTts && !isCloud) finalGeneratedPcm = pcmStream.toByteArray();
						
						try {
							pcmStream.close(); 
						} catch (Exception ignored) {}
						
						final byte[] finalPcm = finalGeneratedPcm;
						final byte[] finalMp3 = finalGeneratedMp3;
						final java.io.File finalPcmFile = generatedPcmFile;
						final long finalPcmBytes = generatedPcmBytes;
						final String finalLoadResult = loadResult;
						final int finalSampleRate = generatedSampleRate;
						final int finalHead = handoffHeadPos[0];
						
						if (getActivity() != null && isAdded()) {
							getActivity().runOnUiThread(() -> {
								if (myGenId != currentGenerationId) return;
								isGenerating = false;
								isEdgeTtsGenerating = false;
								if (activeEdgePcmFile == finalPcmFile) activeEdgePcmFile = null;
								binding.btnGenerate.setEnabled(true);
								binding.btnGenerate.setAlpha(1.0f);
								binding.progressGenerating.setVisibility(android.view.View.GONE);
								
								if ("Success".equals(finalLoadResult)
										&& (isEdgeTts
												? finalPcmFile != null && finalPcmFile.isFile()
														&& finalPcmBytes > 0
												: finalPcm != null && finalPcm.length > 0)) {
									lastGeneratedPcmData = isEdgeTts ? null : finalPcm;
									lastGeneratedMp3Data = finalMp3;
									lastGeneratedPcmFile = isEdgeTts ? finalPcmFile : null;
									lastGeneratedPcmBytes = isEdgeTts ? finalPcmBytes : 0;
									lastGeneratedSampleRate = finalSampleRate;
									isAudioGeneratedForCurrentText = true;
									lastParams = params;
									
									binding.layoutIdleState.setVisibility(android.view.View.GONE);
									binding.layoutGeneratedState.setVisibility(android.view.View.VISIBLE);
									binding.textview69.setText("GENERATION COMPLETE");
									if (isEdgeTts) {
										long totalFrames = finalPcmBytes / 2L;
										double totalSeconds = (double) totalFrames / finalSampleRate;
										final int w = binding.imgWaveform.getWidth() > 0
												? binding.imgWaveform.getWidth() : 800;
										binding.playheadLine.setVisibility(android.view.View.VISIBLE);
										android.graphics.Bitmap waveBmp =
												com.builtbydeep.sovra.WaveformHelper
														.createWaveformBitmap(finalPcmFile, w, 150);
										if (waveBmp != null) {
											binding.imgWaveform.setImageBitmap(waveBmp);
										} else {
											binding.imgWaveform.setImageDrawable(null);
										}
										binding.tvDuration.setText(String.format(java.util.Locale.US,
												"0:00 / %d:%02d", (int) (totalSeconds / 60),
												(int) (totalSeconds % 60)));
										android.media.AudioTrack streamingTrack = liveStreamTrack;
										if (streamingTrack != null) {
											boolean streamPlaying = streamingTrack.getPlayState()
													== android.media.AudioTrack.PLAYSTATE_PLAYING;
											binding.imageview52.setImageResource(streamPlaying
													? R.drawable.icon_pause_circle
													: R.drawable.icon_play_circle);
											binding.textview92.setText(streamPlaying ? "Pause" : "Play");
											if (streamPlaying) {
											android.content.Intent durationIntent =
													new android.content.Intent(getContext(),
															com.builtbydeep.sovra.system.VoxMediaService.class);
											durationIntent.setAction("ACTION_UPDATE_STATE");
											durationIntent.putExtra("title", "Sovra Audio");
											durationIntent.putExtra("subtitle", "Playing...");
											durationIntent.putExtra("state",
													com.builtbydeep.sovra.system.VoxMediaController.STATE_PLAYING);
											durationIntent.putExtra("isLibraryMode", false);
											durationIntent.putExtra("duration",
													(long) (totalSeconds * 1000));
											if (android.os.Build.VERSION.SDK_INT
													>= android.os.Build.VERSION_CODES.O) {
												getContext().startForegroundService(durationIntent);
											} else {
												getContext().startService(durationIntent);
											}
											}
											startEdgePlaybackMonitor(streamingTrack, myEdgePlaybackId,
													totalFrames, totalSeconds);
											if (!streamPlaying && playheadAnimator != null) {
												playheadAnimator.pause();
											}
										} else {
											binding.imageview52.setImageResource(
													R.drawable.icon_play_circle);
											binding.textview92.setText("Play");
										}
									} else {
										final int totalFrames = finalPcm.length / 2;
										final float cloudPlaybackSpeed = isCloudTts && ((com.builtbydeep.sovra.CloudTtsClients.ELEVENLABS.equals(selectedCloudProvider) && com.builtbydeep.sovra.CloudTtsClients.ELEVEN_V3.equals(selectedCloudModel)) || com.builtbydeep.sovra.CloudTtsClients.WITAI.equals(selectedCloudProvider)) ? com.builtbydeep.sovra.CloudTtsClients.clamp(params.speed, 0.25f, 2.0f) : 1.0f;
										final double totalSeconds = (double) totalFrames
												/ finalSampleRate / cloudPlaybackSpeed;
										final int w = binding.imgWaveform.getWidth() > 0
												? binding.imgWaveform.getWidth() : 800;

										android.graphics.Bitmap waveBmp =
												com.builtbydeep.sovra.WaveformHelper
														.createWaveformBitmap(finalPcm, w, 150);
										if (waveBmp != null) binding.imgWaveform.setImageBitmap(waveBmp);

										try {
										if (audioTrack != null) {
											audioTrack.release();
											audioTrack = null;
										}
										
										int staticBufferSize = finalPcm.length > 0 ? finalPcm.length : 1;
										
										audioTrack = new android.media.AudioTrack.Builder()
										.setAudioAttributes(new android.media.AudioAttributes.Builder()
										.setUsage(android.media.AudioAttributes.USAGE_MEDIA)
										.setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
										.build())
										.setAudioFormat(new android.media.AudioFormat.Builder()
										.setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
										.setSampleRate(finalSampleRate)
										.setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO)
										.build())
										.setBufferSizeInBytes(staticBufferSize)
										.setTransferMode(android.media.AudioTrack.MODE_STATIC)
										.build();
										
										audioTrack.write(finalPcm, 0, finalPcm.length);
										if (isCloudTts) {
											float playbackPitch = com.builtbydeep.sovra.CloudTtsClients.SPEECHIFY
													.equals(selectedCloudProvider) ? 1.0f
													: com.builtbydeep.sovra.CloudTtsClients.clamp(
															params.pitch, 0.25f, 2.0f);
											audioTrack.setPlaybackParams(new android.media.PlaybackParams()
													.setSpeed(cloudPlaybackSpeed).setPitch(playbackPitch));
										}
										
										binding.playheadLine.setVisibility(android.view.View.VISIBLE);
										
										android.content.Intent durIntent = new android.content.Intent(getContext(), com.builtbydeep.sovra.system.VoxMediaService.class);
										durIntent.setAction("ACTION_UPDATE_STATE");
										durIntent.putExtra("title", "Sovra Audio");
										durIntent.putExtra("subtitle", "Playing...");
										durIntent.putExtra("state", com.builtbydeep.sovra.system.VoxMediaController.STATE_PLAYING);
										durIntent.putExtra("isLibraryMode", false);
										durIntent.putExtra("duration", (long)(totalSeconds * 1000));
										
										boolean autoStart = isEdgeTts || (isCloudTts && finalHead == 0);
if (autoStart || (finalHead < (totalFrames - 5000) && finalHead > 0)) {
    audioTrack.setPlaybackHeadPosition(autoStart ? 0 : finalHead);
    audioTrack.play();
											
											binding.imageview52.setImageResource(R.drawable.icon_pause_circle);
											binding.textview92.setText("Pause");
											
											if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
												getContext().startForegroundService(durIntent);
											} else {
												getContext().startService(durIntent);
											}
											
											float progressFrac = autoStart ? 0f : (float) finalHead / totalFrames;
											float startX = progressFrac * w;
											binding.playheadLine.setTranslationX(startX);
											
											long remainingMs = (long) ((1.0 - progressFrac) * totalSeconds * 1000);
											
											if (playheadAnimator != null) playheadAnimator.cancel();
											playheadAnimator = android.animation.ValueAnimator.ofFloat(startX, (float) w);
											playheadAnimator.setDuration(remainingMs);
											playheadAnimator.setInterpolator(new android.view.animation.LinearInterpolator());
											playheadAnimator.addUpdateListener(anim -> {
												float tx = (float) anim.getAnimatedValue();
												binding.playheadLine.setTranslationX(tx);
												double elapsed = (tx / w) * totalSeconds;
												binding.tvDuration.setText(String.format(java.util.Locale.US, "%d:%02d / %d:%02d", (int)(elapsed/60), (int)(elapsed%60), (int)(totalSeconds/60), (int)(totalSeconds%60)));
											});
											
											playheadAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
												@Override
												public void onAnimationEnd(android.animation.Animator animation) {
													if (binding.playheadLine.getTranslationX() >= w - 10) {
														binding.imageview52.setImageResource(R.drawable.icon_play_circle);
														binding.textview92.setText("Play");
														binding.playheadLine.setTranslationX(0f);
														binding.tvDuration.setText(String.format(java.util.Locale.US, "0:00 / %d:%02d", (int)(totalSeconds/60), (int)(totalSeconds%60)));
														
														try {
															if (audioTrack != null) {
																audioTrack.pause();
																audioTrack.flush();
																audioTrack.stop();
																audioTrack.reloadStaticData();
																audioTrack.setPlaybackHeadPosition(0);
															}
														} catch (Exception ignored) {}
														
														com.builtbydeep.sovra.system.VoxMediaController.getInstance(getContext()).hideNotification();
													}
												}
											});
											
											playheadAnimator.start();
										} else {
											audioTrack.setPlaybackHeadPosition(totalFrames);
											binding.imageview52.setImageResource(R.drawable.icon_play_circle);
											binding.textview92.setText("Play");
											binding.playheadLine.setTranslationX(0f);
											binding.tvDuration.setText(String.format(java.util.Locale.US, "0:00 / %d:%02d", (int)(totalSeconds/60), (int)(totalSeconds%60)));
											com.builtbydeep.sovra.system.VoxMediaController.getInstance(getContext()).hideNotification();
										}
										
										audioTrack.setNotificationMarkerPosition(totalFrames);
										audioTrack.setPlaybackPositionUpdateListener(new android.media.AudioTrack.OnPlaybackPositionUpdateListener() {
											@Override
											public void onMarkerReached(android.media.AudioTrack track) {
												if (getActivity() != null && isAdded()) {
													getActivity().runOnUiThread(() -> {
														binding.imageview52.setImageResource(R.drawable.icon_play_circle);
														binding.textview92.setText("Play");
														binding.playheadLine.setTranslationX(0f);
														if (playheadAnimator != null) playheadAnimator.cancel();
														
														try {
															track.pause();
															track.flush();
															track.stop();
															track.reloadStaticData();
															track.setPlaybackHeadPosition(0);
														} catch (Exception ignored) {}
														
														com.builtbydeep.sovra.system.VoxMediaController.getInstance(getContext()).hideNotification();
													});
												}
											}
											@Override public void onPeriodicNotification(android.media.AudioTrack track) {}
										});
										
									} catch (Exception ignored) {}
									}
								} else {
									binding.progressGenerating.setVisibility(android.view.View.GONE);
									binding.imageview65.setVisibility(android.view.View.VISIBLE);
									binding.textview69.setText(isEdgeTts || isCloudTts
											? finalLoadResult : "SYNTHESIS FAILED");
									binding.textview69.setTextColor(android.graphics.Color.parseColor("#EF4444"));
									binding.textview92.setText("Play");
									binding.imageview52.setImageResource(R.drawable.icon_play_circle);
									
									com.builtbydeep.sovra.system.VoxMediaController.getInstance(getContext()).hideNotification();
									
									if (getView() != null) {
										String userFriendlyMessage = isEdgeTts || isCloudTts
										? (finalLoadResult == null || finalLoadResult.isEmpty()
												? (isEdgeTts ? "Microsoft Edge TTS could not generate audio."
														: "Cloud TTS could not generate audio.")
												: finalLoadResult)
										: (finalLoadResult != null && (finalLoadResult.contains("missing") || finalLoadResult.contains("empty")))
												? "Please select a valid voice model to continue."
												: "Unable to generate voice at the moment. Please try again.";
										
										com.google.android.material.snackbar.Snackbar.make(getView(), userFriendlyMessage, com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
										.setBackgroundTint(android.graphics.Color.parseColor("#EF4444")).setTextColor(android.graphics.Color.WHITE).show();
									}
								}
							});
						} else if (generatedPcmFile != null) {
							generatedPcmFile.delete();
							if (activeEdgePcmFile == generatedPcmFile) activeEdgePcmFile = null;
						}
					}).start();
					if (isEdgeTts) {
						sp3.edit().putString("last_edge_voice", selectedEdgeVoice).apply();
					} else if (isCloudTts) {
						sp3.edit().putString("last_cloud_voice",
										sp1.getString("cloud_voice_" + selectedCloudProvider, ""))
								.putString("last_cloud_model", selectedCloudModel).apply();
					}
				}
				
			}
		});
		
		binding.save.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View _view) {
				_saveAudioAction();
			}
		});
		
		binding.etInput.addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence _param1, int _param2, int _param3, int _param4) {
				final String _charSeq = _param1.toString();
				
				int length = _charSeq.length();
				
				// NAYA: Hidden 25000 character limit
				if (length > 25000) {
					binding.etInput.setText(_charSeq.substring(0, 25000));
					binding.etInput.setSelection(25000);
				}
				
				// Reset state on text change
				String currentText = binding.etInput.getText().toString().trim();
				
				if (!currentText.equals(lastGeneratedText) && !isGenerating) {
					isAudioGeneratedForCurrentText = false;
					
					// UI STATE RESET
					binding.layoutIdleState.setVisibility(android.view.View.VISIBLE);
					binding.layoutGeneratedState.setVisibility(android.view.View.GONE);
					binding.progressGenerating.setVisibility(android.view.View.GONE);
					binding.imageview65.setVisibility(android.view.View.VISIBLE);
					
					binding.textview92.setText("Play");
					binding.imageview52.setImageResource(R.drawable.icon_play_circle); 
					
					binding.textview69.setText("READY TO SYNTHESIZE");
					binding.textview69.setTextColor(android.graphics.Color.parseColor("#94A3B8"));
					
					if (audioTrack != null && audioTrack.getState() != android.media.AudioTrack.STATE_UNINITIALIZED) {
						try { audioTrack.stop(); } catch (Exception e) {}
					}
					if (lastGeneratedPcmFile != null || edgeStreamStarted) {
						edgePlaybackId++;
						edgeStreamStarted = false;
						try {
							if (liveStreamTrack != null) {
								liveStreamTrack.pause();
								liveStreamTrack.flush();
								liveStreamTrack.stop();
								liveStreamTrack.release();
								liveStreamTrack = null;
							}
						} catch (Exception error) {
							android.util.Log.w("GenerateFragmentActivity",
									"Unable to stop previous Edge TTS playback.", error);
						}
						if (lastGeneratedPcmFile != null) lastGeneratedPcmFile.delete();
						lastGeneratedPcmFile = null;
						lastGeneratedPcmBytes = 0;
					}
					if (playheadAnimator != null) playheadAnimator.cancel();
				}
				
			}
			
			@Override
			public void beforeTextChanged(CharSequence _param1, int _param2, int _param3, int _param4) {
				
			}
			
			@Override
			public void afterTextChanged(Editable _param1) {
				
			}
		});
		
		binding.opneDropdown.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View _view) {
				// Use the exact model type instead of guessing from the model name
				String currentModelType = sp1.getString("active_model_type", "vits");
				boolean kokoroActive = _isKokoroModel(currentModelType);
				if (kokoroActive) return;
				
				String activePath = sp1.getString("active_model", "");
				int activeSpeakerCount = sp1.getInt("active_speaker_count", 1);
				
				// Return silently if no model is selected
				if (activePath.isEmpty()) {
					return;
				}
				
				// Show snackbar only if it is explicitly a Piper model and has 1 or fewer speakers
				if (!kokoroActive && activeSpeakerCount <= 1) {
					if (_view != null) {
						com.google.android.material.snackbar.Snackbar
						.make(_view, "This Voice model has only one voice.",
						com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
						.setBackgroundTint(android.graphics.Color.parseColor("#1A1D2E"))
						.setTextColor(android.graphics.Color.WHITE)
						.show();
					}
					return;
				}
				
				if (listPopupWindow != null) {
					if (listPopupWindow.isShowing()) {
						listPopupWindow.dismiss();
					} else {
						if (kokoroActive) {
							int savedSpeakerId = sp1.getInt("active_kokoro_speaker", 31);
							java.util.List<com.builtbydeep.sovra.KokoroVoiceHelper.VoiceItem> voices =
							_getKokoroVoices();
							
							for (int i = 0; i < voices.size(); i++) {
								if (voices.get(i).speakerId == savedSpeakerId) {
									listPopupWindow.setSelection(i);
									break;
								}
							}
						} else if (currentModelType.equalsIgnoreCase("supertonic")) {
							listPopupWindow.setSelection(Math.max(0, Math.min(
									activeSpeakerCount - 1, sp1.getInt("active_supertonic_speaker", 0))));
						} else {
							// Piper multi-speaker selection logic
							int savedPiperSpeakerId = sp1.getInt("active_piper_speaker", 0);
							listPopupWindow.setSelection(savedPiperSpeakerId);
						}
						listPopupWindow.show();
					}
				}
				
			}
		});
		
		binding.btnExport.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View _view) {
				_saveAudioAction();
			}
		});
	}
	
	private void initializeLogic() {
		com.builtbydeep.sovra.system.VoxMediaController.getInstance(getContext()).setGenerateListener(new com.builtbydeep.sovra.system.VoxMediaController.MediaCommandListener() {
			
			android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
			
			@Override
			public void onPlay() {
				mainHandler.post(() -> { try { _toggleGeneratePlayback(); } catch(Exception e) {} });
			}
			
			@Override
			public void onPause() {
				mainHandler.post(() -> { try { _toggleGeneratePlayback(); } catch(Exception e) {} });
			}
			
			@Override
			public void onStop() {
				mainHandler.post(() -> { try { _cancelGeneration(); } catch(Exception e) {} });
			}
			
			@Override
			public void onNext() {} 
			
			@Override
			public void onPrevious() {} 
		});
		
		// AUDIO FOCUS LOGIC
		audioManager = (android.media.AudioManager) getContext().getSystemService(android.content.Context.AUDIO_SERVICE);
		
		focusListener = focusChange -> {
			if (focusChange == android.media.AudioManager.AUDIOFOCUS_LOSS || 
			focusChange == android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
				
				if (liveStreamTrack != null && liveStreamTrack.getPlayState() == android.media.AudioTrack.PLAYSTATE_PLAYING) {
					liveStreamTrack.pause();
				}
				
				if (audioTrack != null && audioTrack.getPlayState() == android.media.AudioTrack.PLAYSTATE_PLAYING) {
					audioTrack.pause();
					if (getActivity() != null) {
						getActivity().runOnUiThread(() -> {
							binding.imageview52.setImageResource(R.drawable.icon_play_circle);
							binding.textview92.setText("Play");
							if (playheadAnimator != null) playheadAnimator.pause();
							com.builtbydeep.sovra.system.VoxMediaController.getInstance(getContext()).updatePlaybackState("Sovra Audio", "Paused", com.builtbydeep.sovra.system.VoxMediaController.STATE_PAUSED, false);
						});
					}
				}
			} else if (focusChange == android.media.AudioManager.AUDIOFOCUS_GAIN) {
				if (liveStreamTrack != null) liveStreamTrack.play();
			}
		};
		
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
			focusRequest = new android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN)
			.setOnAudioFocusChangeListener(focusListener)
			.build();
		}
		
		String activeModel = sp1.getString("active_model", "");
		String activeModelType = sp1.getString("active_model_type", "vits");
		String activeModelName = sp1.getString("active_model_name", "Custom Voice");
		int activeSpeakerCount = sp1.getInt("active_speaker_count", 1);
		boolean isKokoro = _isKokoroModel(activeModelType);
		boolean isSupertonic = activeModelType.equalsIgnoreCase("supertonic");
		boolean isEdgeTts = com.builtbydeep.sovra.EdgeTtsClient.MODEL_TYPE
				.equalsIgnoreCase(activeModelType);
		boolean isCloudTts = !cloudProviderFromType(activeModelType).isEmpty();
		if (activeModel.isEmpty()) {
			binding.voiceNameTv.setText("No Model Selected");
			binding.voiceNameTv.setTextColor(android.graphics.Color.parseColor("#EF4444"));
			binding.opneDropdown.setVisibility(android.view.View.GONE);
		} else {
			if (isKokoro || isSupertonic || activeSpeakerCount > 1) {
				binding.opneDropdown.setVisibility(android.view.View.VISIBLE);
			} else {
				binding.opneDropdown.setVisibility(android.view.View.GONE);
			}
			if (isEdgeTts) {
				String voice = sp1.getString("edge_voice", "hi-IN-SwaraNeural");
				String gender = sp1.getString("edge_voice_gender", "Female");
				binding.voiceNameTv.setText(
						com.builtbydeep.sovra.EdgeTtsClient.formatDisplayName(voice, gender));
			} else if (isCloudTts) {
				String providerId = cloudProviderFromType(activeModelType);
				binding.voiceNameTv.setText(sp1.getString("cloud_voice_" + providerId,
						"").isEmpty() ? "Select cloud voice" : sp1.getString(
								"cloud_voice_" + providerId, ""));
			} else if (isSupertonic) {
				int speakerId = sp1.getInt("active_supertonic_speaker", 0);
				com.builtbydeep.sovra.SupertonicEngine.getInstance().setActiveSpeakerId(speakerId);
				binding.voiceNameTv.setText(getSupertonicVoiceProfile(speakerId));
			} else if (isKokoro) {
				try {
					int savedSpeakerId = sp1.getInt("active_kokoro_speaker", 31);
					com.builtbydeep.sovra.KokoroEngine.getInstance().setActiveSpeakerId(savedSpeakerId);
					String voiceName = _getKokoroVoiceName(savedSpeakerId);
					binding.voiceNameTv.setText((voiceName != null && !voiceName.isEmpty()) ? voiceName : "Kokoro Voice");
				} catch (Throwable t) {
					binding.voiceNameTv.setText("Kokoro Voice");
				}
			} else {
				String piperLang = "Unknown";
				String piperGender = "Unknown";
				String modelLabel = "Piper";
				try {
					String modelsDataRaw = sp1.getString("models_data", "[]");
					if (!modelsDataRaw.equals("[]")) {
						java.util.ArrayList<java.util.HashMap<String, Object>> mList =
						new com.google.gson.Gson().fromJson(modelsDataRaw,
						new com.google.gson.reflect.TypeToken<java.util.ArrayList<java.util.HashMap<String, Object>>>(){}.getType());
						if (mList != null) {
							for (java.util.HashMap<String, Object> model : mList) {
								String onnxPath = model.containsKey("onnx_path") && model.get("onnx_path") != null ? model.get("onnx_path").toString() : "";
								if (activeModel.equals(onnxPath)) {
									if (model.containsKey("language") && model.get("language") != null) {
										piperLang = model.get("language").toString().trim();
									}
									if (model.containsKey("gender") && model.get("gender") != null) {
										piperGender = model.get("gender").toString().trim();
									}
									if (model.containsKey("type") && model.get("type").toString().contains("MMS")) {
										modelLabel = "MMS";
									}
									break;
								}
							}
						}
					}
				} catch (Exception e) {}
				if (!piperLang.equals("Unknown") && piperLang.length() > 0) {
					piperLang = piperLang.substring(0, 1).toUpperCase() + piperLang.substring(1).toLowerCase();
				}
				if (!piperGender.equals("Unknown") && piperGender.length() > 0) {
					piperGender = piperGender.substring(0, 1).toUpperCase() + piperGender.substring(1).toLowerCase();
				}
				if (activeSpeakerCount > 1) {
					int currentPiperId = sp1.getInt("active_piper_speaker", 0);
					binding.voiceNameTv.setText(modelLabel + " • Speaker " + currentPiperId + " • " + piperLang);
				} else {
					binding.voiceNameTv.setText(modelLabel + " • " + piperGender + " • " + piperLang);
				}
			}
			binding.voiceNameTv.setTextColor(android.graphics.Color.WHITE);
		}
		listPopupWindow = new androidx.appcompat.widget.ListPopupWindow(getContext());
		listPopupWindow.setAnchorView(binding.voiceNameTv);
		listPopupWindow.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.parseColor("#1A1D2E")));
		java.util.List<String> voiceNames = new java.util.ArrayList<>();
		if (isKokoro) {
			java.util.List<com.builtbydeep.sovra.KokoroVoiceHelper.VoiceItem> allVoices = _getKokoroVoices();
			for (com.builtbydeep.sovra.KokoroVoiceHelper.VoiceItem vItem : allVoices) {
				try {
					String label = vItem.getFullLabel();
					voiceNames.add((label != null && !label.isEmpty()) ? label : "Unknown Voice");
				} catch (Throwable t) {
					voiceNames.add("Unknown Voice");
				}
			}
		} else if (isSupertonic || activeSpeakerCount > 1) {
			for (int i = 0; i < activeSpeakerCount; i++) {
				voiceNames.add(isSupertonic ? getSupertonicVoiceProfile(i) : "Speaker " + i);
			}
		}
		voiceAdapter = new android.widget.ArrayAdapter<>(getContext(), R.layout.dropdown_item, R.id.text1, voiceNames);
		listPopupWindow.setAdapter(voiceAdapter);
		listPopupWindow.setOnItemClickListener((parent, view, position, id) -> {
			try {
				String selectedLabel = voiceAdapter.getItem(position);
				if (selectedLabel == null) return;
				String currentType = sp1.getString("active_model_type", "vits");
				boolean currentlyKokoro = _isKokoroModel(currentType);
				boolean currentlySupertonic = currentType.equalsIgnoreCase("supertonic");
				if (currentlyKokoro) {
					binding.voiceNameTv.setText(selectedLabel);
					for (com.builtbydeep.sovra.KokoroVoiceHelper.VoiceItem vItem : _getKokoroVoices()) {
						try {
							if (selectedLabel.equals(vItem.getFullLabel())) {
								com.builtbydeep.sovra.KokoroEngine.getInstance().setActiveSpeakerId(vItem.speakerId);
								sp1.edit().putInt("active_kokoro_speaker", vItem.speakerId).apply();
								break;
							}
						} catch (Throwable t) {}
					}
				} else if (currentlySupertonic) {
					sp1.edit().putInt("active_supertonic_speaker", position).apply();
					com.builtbydeep.sovra.SupertonicEngine.getInstance().setActiveSpeakerId(position);
					binding.voiceNameTv.setText(getSupertonicVoiceProfile(position));
				} else {
					sp1.edit().putInt("active_piper_speaker", position).apply();
					com.builtbydeep.sovra.VoiceEngine.getInstance().setActiveSpeakerId(position);
					String currentText = binding.voiceNameTv.getText().toString();
					String langPart = "Unknown";
					String modelPart = "Piper";
					String[] parts = currentText.split(" • ");
					if (parts.length >= 1) modelPart = parts[0];
					if (parts.length >= 3) langPart = parts[2];
					binding.voiceNameTv.setText(modelPart + " • Speaker " + position + " • " + langPart);
				}
				try { com.builtbydeep.sovra.VoiceEngine.getInstance().cancel(); } catch (Throwable ignored) {}
				try { com.builtbydeep.sovra.KokoroEngine.getInstance().cancel(); } catch (Throwable ignored) {}
				try { com.builtbydeep.sovra.SupertonicEngine.getInstance().cancel(); } catch (Throwable ignored) {}
				currentGenerationId++;
				isCancelled = true;
				isGenerating = false;
				isAudioGeneratedForCurrentText = false;
				try {
					if (audioTrack != null) {
						audioTrack.stop();
						audioTrack.flush();
						audioTrack.release();
						audioTrack = null;
					}
				} catch (Exception ignored) {}
				try {
					if (liveStreamTrack != null) {
						liveStreamTrack.stop();
						liveStreamTrack.release();
						liveStreamTrack = null;
					}
				} catch (Exception ignored) {}
				lastGeneratedPcmData = null;
				lastGeneratedMp3Data = null;
				lastParams = null;
				if (playheadAnimator != null) playheadAnimator.cancel();
				binding.playheadLine.setTranslationX(0f);
				binding.btnGenerate.setAlpha(1.0f);
				binding.btnGenerate.setEnabled(true);
				binding.layoutGeneratedState.setVisibility(android.view.View.GONE);
				binding.layoutIdleState.setVisibility(android.view.View.VISIBLE);
				binding.progressGenerating.setVisibility(android.view.View.GONE);
				binding.imageview65.setVisibility(android.view.View.VISIBLE);
				binding.textview69.setText("READY TO GENERATE");
				binding.textview69.setTextColor(android.graphics.Color.parseColor("#94A3B8"));
				binding.textview92.setText("Play");
				binding.imageview52.setImageResource(R.drawable.icon_play_circle);
			} catch (Throwable t) {}
			listPopupWindow.dismiss();
		});
		
		binding.imgWaveform.setOnTouchListener((v, motionEvent) -> {
			if (lastGeneratedPcmData == null || audioTrack == null) return false;
			
			int w = binding.imgWaveform.getWidth();
			if (w <= 0) return false;
			
			int totalFrames = lastGeneratedPcmData.length / 2;
			double totalSeconds = (double) totalFrames / lastGeneratedSampleRate;
			int totalMin = (int)(totalSeconds / 60);
			int totalSec = (int)(totalSeconds % 60);
			
			float touchX = Math.max(0f, Math.min(motionEvent.getX(), (float) w));
			float fraction = touchX / w;
			int targetFrame = (int)(fraction * totalFrames);
			
			if (motionEvent.getAction() == android.view.MotionEvent.ACTION_DOWN) {
				if (playheadAnimator != null) playheadAnimator.pause();
				try { audioTrack.pause(); } catch (Exception ignored) {}
			}
			
			if (motionEvent.getAction() == android.view.MotionEvent.ACTION_DOWN
			|| motionEvent.getAction() == android.view.MotionEvent.ACTION_MOVE) {
				binding.playheadLine.setTranslationX(touchX);
				double seekSec = fraction * totalSeconds;
				binding.tvDuration.setText(String.format(java.util.Locale.US, "%d:%02d / %d:%02d",
				(int)(seekSec / 60), (int)(seekSec % 60), totalMin, totalSec));
			}
			
			if (motionEvent.getAction() == android.view.MotionEvent.ACTION_UP
			|| motionEvent.getAction() == android.view.MotionEvent.ACTION_CANCEL) {
				try {
					audioTrack.stop();
					audioTrack.reloadStaticData();
					audioTrack.setPlaybackHeadPosition(targetFrame);
					
					// 🚀 FIX: Naya object nahi banega — fragment-level same object reuse hoga
					_requestAudioFocus();
					
					audioTrack.play();
				} catch (Exception ignored) {}
				
				float remainingSec = (float)(totalFrames - targetFrame) / lastGeneratedSampleRate;
				if (remainingSec < 0) remainingSec = 0f;
				
				if (playheadAnimator != null) playheadAnimator.cancel();
				playheadAnimator = android.animation.ValueAnimator.ofFloat(touchX, (float) w);
				playheadAnimator.setDuration((long)(remainingSec * 1000));
				playheadAnimator.setInterpolator(new android.view.animation.LinearInterpolator());
				playheadAnimator.addUpdateListener(anim -> {
					float tx = (float) anim.getAnimatedValue();
					binding.playheadLine.setTranslationX(tx);
					// tvDuration live update during playback after seek
					double elapsed = (tx / w) * totalSeconds;
					binding.tvDuration.setText(String.format(java.util.Locale.US, "%d:%02d / %d:%02d",
					(int)(elapsed / 60), (int)(elapsed % 60), totalMin, totalSec));
				});
				playheadAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
					@Override
					public void onAnimationEnd(android.animation.Animator animation) {
						if (binding.playheadLine.getTranslationX() >= w - 10) {
							binding.imageview52.setImageResource(R.drawable.icon_play_circle);
							binding.textview92.setText("Play");
							binding.playheadLine.setTranslationX(0f);
							binding.tvDuration.setText(String.format(java.util.Locale.US,
							"0:00 / %d:%02d", totalMin, totalSec));
							try {
								if (audioTrack != null) {
									audioTrack.stop();
									audioTrack.reloadStaticData();
								}
							} catch (Exception ignored) {}
						}
					}
				});
				playheadAnimator.start();
				
				binding.imageview52.setImageResource(R.drawable.icon_pause_circle);
				binding.textview92.setText("Pause");
			}
			
			return true;
		});
		
		_updateSupertonicLanguageSelector();
	}

	private String _getSupertonicLanguageName(String languageCode) {
		String normalizedLanguage = com.builtbydeep.sovra.SupertonicEngine.normalizeLanguage(languageCode);
		String languageName = java.util.Locale.forLanguageTag(normalizedLanguage)
				.getDisplayLanguage(java.util.Locale.ENGLISH);
		return languageName.isEmpty() ? normalizedLanguage.toUpperCase(java.util.Locale.ROOT) : languageName;
	}

	private void _updateSupertonicLanguageSelector() {
		if (binding == null || sp1 == null) return;
		boolean isSupertonic = !sp1.getString("active_model", "").isEmpty()
				&& sp1.getString("active_model_type", "vits").equalsIgnoreCase("supertonic");
		binding.supertonicControlsLayout.setVisibility(isSupertonic ? android.view.View.VISIBLE : android.view.View.GONE);
		binding.voiceInfoCard.setVisibility(isSupertonic ? android.view.View.GONE : android.view.View.VISIBLE);
		binding.kokoroVoiceFilters.setVisibility(
				isSupertonic || !sp1.getString("active_model_type", "vits").equalsIgnoreCase("kokoro")
						? android.view.View.GONE : android.view.View.VISIBLE);
		if (!isSupertonic) return;
		updateSupertonicControlLabels();
	}

	private void updateSupertonicControlLabels() {
		if (binding == null || sp1 == null || sp3 == null) return;
		String language = SupertonicEngine.normalizeLanguage(sp1.getString("active_language", "en"));
		int speakerId = sp1.getInt("active_supertonic_speaker", 0);
		String profile = getSupertonicVoiceProfile(speakerId);
		int separator = profile.indexOf(" - ");
		String styleCode = separator >= 0 ? profile.substring(0, separator) : profile;
		binding.supertonicVoiceValue.setText(
				"Supertonic • " + styleCode + " " + _getSupertonicLanguageName(language));
	}

	private void showSupertonicQualityDialog() {
		if (getContext() == null) return;
		int savedSteps = Math.max(SupertonicEngine.MIN_GENERATION_STEPS,
				Math.min(SupertonicEngine.MAX_GENERATION_STEPS,
						sp3.getInt("supertonic_num_steps", SupertonicEngine.DEFAULT_GENERATION_STEPS)));
		android.view.View content = getLayoutInflater().inflate(R.layout.dialog_supertonic_quality, null);
		android.widget.SeekBar seekBar = content.findViewById(R.id.supertonic_quality_seekbar);
		android.widget.TextView value = content.findViewById(R.id.supertonic_quality_value);
		seekBar.setMax(SupertonicEngine.MAX_GENERATION_STEPS - SupertonicEngine.MIN_GENERATION_STEPS);
		seekBar.setProgress(savedSteps - SupertonicEngine.MIN_GENERATION_STEPS);
		value.setText(savedSteps + " steps");
		seekBar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(android.widget.SeekBar bar, int progress, boolean fromUser) {
				value.setText((progress + SupertonicEngine.MIN_GENERATION_STEPS) + " steps");
			}

			@Override
			public void onStartTrackingTouch(android.widget.SeekBar bar) {}

			@Override
			public void onStopTrackingTouch(android.widget.SeekBar bar) {}
		});
		new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
				.setTitle("Generation Quality")
				.setView(content)
				.setNegativeButton("Cancel", null)
				.setPositiveButton("Done", (dialog, which) -> {
					int selectedSteps = seekBar.getProgress() + SupertonicEngine.MIN_GENERATION_STEPS;
					sp3.edit().putInt("supertonic_num_steps", selectedSteps).apply();
					updateSupertonicControlLabels();
					_forceResetToIdle();
				})
				.show();
	}

	private void showSupertonicVoiceSheet() {
		if (getContext() == null) return;
		com.google.android.material.bottomsheet.BottomSheetDialog dialog =
				new com.google.android.material.bottomsheet.BottomSheetDialog(requireContext());
		android.view.View content = getLayoutInflater().inflate(R.layout.bottom_sheet_supertonic, null);
		android.widget.Spinner languageSpinner = content.findViewById(R.id.supertonic_language_spinner);
		android.widget.LinearLayout voiceList = content.findViewById(R.id.supertonic_voice_list);
		String selectedLanguage = SupertonicEngine.normalizeLanguage(sp1.getString("active_language", "en"));
		java.util.List<String> languageCodes = new java.util.ArrayList<>(
				java.util.Arrays.asList(SupertonicEngine.getSupportedLanguages()));
		java.util.Collections.sort(languageCodes,
				java.util.Comparator.comparing(this::_getSupertonicLanguageName));
		java.util.List<String> languageNames = new java.util.ArrayList<>();
		int selectedLanguagePosition = 0;
		for (int i = 0; i < languageCodes.size(); i++) {
			String code = languageCodes.get(i);
			languageNames.add(_getSupertonicLanguageName(code));
			if (selectedLanguage.equals(code)) selectedLanguagePosition = i;
		}
		android.widget.ArrayAdapter<String> languageAdapter = new android.widget.ArrayAdapter<>(
				requireContext(), android.R.layout.simple_spinner_item, languageNames);
		languageAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		languageSpinner.setAdapter(languageAdapter);
		languageSpinner.setSelection(selectedLanguagePosition, false);
		languageSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view,
					int position, long id) {
				String newLanguage = languageCodes.get(position);
				String oldLanguage = SupertonicEngine.normalizeLanguage(sp1.getString("active_language", "en"));
				if (!newLanguage.equals(oldLanguage)) {
					sp1.edit().putString("active_language", newLanguage).apply();
					SupertonicEngine.getInstance().setActiveLanguage(newLanguage);
					updateSupertonicControlLabels();
					_forceResetToIdle();
				}
			}

			@Override
			public void onNothingSelected(android.widget.AdapterView<?> parent) {}
		});

		int selectedSpeaker = sp1.getInt("active_supertonic_speaker", 0);
		for (int i = 0; i < SUPERTONIC_VOICE_PROFILES.length; i++) {
			final int speakerId = i;
			com.google.android.material.card.MaterialCardView row =
					new com.google.android.material.card.MaterialCardView(requireContext());
			android.widget.LinearLayout.LayoutParams rowParams = new android.widget.LinearLayout.LayoutParams(
					android.view.ViewGroup.LayoutParams.MATCH_PARENT,
					android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
			rowParams.bottomMargin = Math.round(6 * getResources().getDisplayMetrics().density);
			row.setLayoutParams(rowParams);
			row.setClickable(true);
			row.setFocusable(true);
			row.setForeground(androidx.core.content.ContextCompat.getDrawable(
					requireContext(), android.R.drawable.list_selector_background));
			row.setCardBackgroundColor(android.graphics.Color.parseColor(
					i == selectedSpeaker ? "#252A40" : "#1A1D2E"));
			row.setStrokeColor(android.graphics.Color.parseColor(
					i == selectedSpeaker ? "#4A9EFF" : "#2A2F45"));
			row.setStrokeWidth(Math.round(getResources().getDisplayMetrics().density));
			row.setRadius(Math.round(12 * getResources().getDisplayMetrics().density));

			android.widget.TextView label = new android.widget.TextView(requireContext());
			label.setText(SUPERTONIC_VOICE_PROFILES[i]);
			label.setTextColor(android.graphics.Color.WHITE);
			label.setTextSize(15);
			label.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
			int horizontalPadding = Math.round(16 * getResources().getDisplayMetrics().density);
			int verticalPadding = Math.round(16 * getResources().getDisplayMetrics().density);
			label.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding);
			row.addView(label);
			row.setOnClickListener(view -> {
				sp1.edit().putInt("active_supertonic_speaker", speakerId).apply();
				SupertonicEngine.getInstance().setActiveSpeakerId(speakerId);
				updateSupertonicControlLabels();
				_forceResetToIdle();
				dialog.dismiss();
			});
			voiceList.addView(row);
		}

		dialog.setContentView(content);
		dialog.show();
	}

	private static String getSupertonicVoiceProfile(int speakerId) {
		return speakerId >= 0 && speakerId < SUPERTONIC_VOICE_PROFILES.length
				? SUPERTONIC_VOICE_PROFILES[speakerId] : "Speaker " + speakerId;
	}
	
	@Override
	public void onActivityResult(int _requestCode, int _resultCode, Intent _data) {
		super.onActivityResult(_requestCode, _resultCode, _data);
		if (_requestCode == 100) {
			if (_resultCode == android.app.Activity.RESULT_OK && _data != null) {
				java.util.ArrayList<String> result = _data.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS);
				if (result != null && !result.isEmpty()) {
					String spokenText = result.get(0);
					String currentText = binding.etInput.getText().toString();
					String finalText;
					if (!currentText.trim().isEmpty()) {
						finalText = currentText + " " + spokenText;
					} else {
						finalText = spokenText;
					}
					if (finalText.length() > 25000) {
						finalText = finalText.substring(0, 25000);
					}
					binding.etInput.setText(finalText);
					binding.etInput.setSelection(finalText.length());
				}
			}
		}
		if (_requestCode == 101) {
			if (_resultCode == android.app.Activity.RESULT_OK && _data != null && _data.getData() != null) {
				android.net.Uri fileUri = _data.getData();
				boolean isPdf = false;
				String mimeType = getContext().getContentResolver().getType(fileUri);
				if ("application/pdf".equalsIgnoreCase(mimeType)) {
					isPdf = true;
				} else {
					String path = fileUri.getPath();
					if (path != null && path.toLowerCase().endsWith(".pdf")) {
						isPdf = true;
					}
				}
				com.builtbydeep.sovra.TextImportHelper._readDocument(getContext(), fileUri, isPdf, new com.builtbydeep.sovra.TextImportHelper.TextImportCallback() {
					@Override
					public void onSuccess(String text) {
						if (text != null) {
							String formattedText = text.trim();
							if (formattedText.length() > 25000) {
								formattedText = formattedText.substring(0, 25000);
							}
							binding.etInput.setText(formattedText);
							binding.etInput.setSelection(formattedText.length());
						}
					}
					@Override
					public void onError(String errorMessage) {
						if (getView() != null) {
							com.google.android.material.snackbar.Snackbar.make(getView(), errorMessage, com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
							.setBackgroundTint(android.graphics.Color.parseColor("#EF4444"))
							.setTextColor(android.graphics.Color.WHITE)
							.show();
						}
					}
				});
			}
		}
		
		switch (_requestCode) {
			
			default:
			break;
		}
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		if (isEdgeTtsGenerating) {
			isCancelled = true;
			currentGenerationId++;
			edgeTtsClient.cancel();
		}
		edgePlaybackId++;
		if (edgeStreamStarted && liveStreamTrack != null) {
			try {
				liveStreamTrack.pause();
				liveStreamTrack.flush();
				liveStreamTrack.stop();
				liveStreamTrack.release();
			} catch (Exception error) {
				android.util.Log.w("GenerateFragmentActivity",
						"Unable to release Edge TTS playback on destroy.", error);
			}
			liveStreamTrack = null;
			edgeStreamStarted = false;
		}
		if (lastGeneratedPcmFile != null) {
			lastGeneratedPcmFile.delete();
			lastGeneratedPcmFile = null;
		}
		if (activeEdgePcmFile != null) {
			activeEdgePcmFile.delete();
			activeEdgePcmFile = null;
		}
		// Fragment me sirf AudioTrack aur UI Animator ko release karna hai
		if (audioTrack != null) {
			try {
				if (audioTrack.getState() != android.media.AudioTrack.STATE_UNINITIALIZED) {
					audioTrack.stop();
				}
				audioTrack.release();
				audioTrack = null;
			} catch (Exception ignored) {}
		}
		
		if (playheadAnimator != null) {
			playheadAnimator.cancel();
			playheadAnimator = null;
		}
		
	}
	
	private boolean _isKokoroModel(String modelType) {
		return "kokoro".equalsIgnoreCase(modelType);
	}

	private java.util.List<com.builtbydeep.sovra.KokoroVoiceHelper.VoiceItem> _getKokoroVoices() {
		return com.builtbydeep.sovra.KokoroVoiceHelper.getAllVoices();
	}

	private String _getKokoroVoiceName(int speakerId) {
		for (com.builtbydeep.sovra.KokoroVoiceHelper.VoiceItem voice : _getKokoroVoices()) {
			if (voice.speakerId == speakerId) return voice.getFullLabel();
		}
		return "Kokoro Voice";
	}

	private void updateKokoroVoiceFilters(boolean isKokoro, int selectedSpeakerId) {
		binding.kokoroVoiceFilters.setVisibility(isKokoro ? android.view.View.VISIBLE : android.view.View.GONE);
		boolean isSupertonic = sp1.getString("active_model_type", "vits").equalsIgnoreCase("supertonic")
				&& !sp1.getString("active_model", "").isEmpty();
		binding.voiceInfoCard.setVisibility(isKokoro || isSupertonic
				? android.view.View.GONE : android.view.View.VISIBLE);
		if (!isKokoro) return;

		binding.opneDropdown.setVisibility(android.view.View.GONE);
		kokoroRegions = new java.util.ArrayList<>(
				com.builtbydeep.sovra.KokoroVoiceHelper.getAvailableRegions());
		java.util.ArrayList<String> regionLabels = new java.util.ArrayList<>();
		for (com.builtbydeep.sovra.KokoroVoiceHelper.RegionItem region : kokoroRegions) {
			regionLabels.add(region.getLabel());
		}

		suppressKokoroFilterCallbacks = true;
		binding.kokoroRegionSpinner.setOnItemSelectedListener(null);
		binding.kokoroVoiceSpinner.setOnItemSelectedListener(null);
		binding.kokoroRegionSpinner.setAdapter(createKokoroSpinnerAdapter(regionLabels));

		com.builtbydeep.sovra.KokoroVoiceHelper.VoiceItem activeVoice =
				com.builtbydeep.sovra.KokoroVoiceHelper.getById(selectedSpeakerId);
		if (activeVoice == null) {
			activeVoice = com.builtbydeep.sovra.KokoroVoiceHelper.getDefaultVoice();
			selectedSpeakerId = activeVoice.speakerId;
			sp1.edit().putInt("active_kokoro_speaker", selectedSpeakerId).apply();
		}
		int selectedRegionPosition = 0;
		for (int i = 0; i < kokoroRegions.size(); i++) {
			if (kokoroRegions.get(i).regionCode.equals(activeVoice.regionCode)) {
				selectedRegionPosition = i;
				break;
			}
		}
		binding.kokoroRegionSpinner.setSelection(selectedRegionPosition, false);
		bindKokoroVoices(kokoroRegions.get(selectedRegionPosition).regionCode, selectedSpeakerId);
		binding.voiceNameTv.setText(activeVoice.getFullLabel());
		com.builtbydeep.sovra.KokoroEngine.getInstance().setActiveSpeakerId(selectedSpeakerId);
		suppressKokoroFilterCallbacks = false;

		binding.kokoroRegionSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view,
					int position, long id) {
				if (suppressKokoroFilterCallbacks || position < 0 || position >= kokoroRegions.size()) return;
				bindKokoroVoices(kokoroRegions.get(position).regionCode,
						sp1.getInt("active_kokoro_speaker", 31));
			}

			@Override
			public void onNothingSelected(android.widget.AdapterView<?> parent) {}
		});
		binding.kokoroVoiceSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view,
					int position, long id) {
				if (suppressKokoroFilterCallbacks || position < 0 || position >= kokoroFilteredVoices.size()) return;
				selectKokoroVoice(kokoroFilteredVoices.get(position));
			}

			@Override
			public void onNothingSelected(android.widget.AdapterView<?> parent) {}
		});
	}

	private android.widget.ArrayAdapter<String> createKokoroSpinnerAdapter(java.util.List<String> labels) {
		android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
				requireContext(), R.layout.kokoro_spinner_item, R.id.text1, labels);
		adapter.setDropDownViewResource(R.layout.dropdown_item);
		return adapter;
	}

	private void bindKokoroVoices(String regionCode, int preferredSpeakerId) {
		kokoroFilteredVoices = new java.util.ArrayList<>(
				com.builtbydeep.sovra.KokoroVoiceHelper.getVoicesByRegionCode(regionCode));
		if (kokoroFilteredVoices.isEmpty()) return;

		java.util.ArrayList<String> voiceLabels = new java.util.ArrayList<>();
		int selectedPosition = 0;
		for (int i = 0; i < kokoroFilteredVoices.size(); i++) {
			com.builtbydeep.sovra.KokoroVoiceHelper.VoiceItem voice = kokoroFilteredVoices.get(i);
			voiceLabels.add(voice.displayName + " • " + voice.gender);
			if (voice.speakerId == preferredSpeakerId) selectedPosition = i;
		}
		suppressKokoroFilterCallbacks = true;
		binding.kokoroVoiceSpinner.setAdapter(createKokoroSpinnerAdapter(voiceLabels));
		binding.kokoroVoiceSpinner.setSelection(selectedPosition, false);
		suppressKokoroFilterCallbacks = false;
		selectKokoroVoice(kokoroFilteredVoices.get(selectedPosition));
	}

	private void selectKokoroVoice(com.builtbydeep.sovra.KokoroVoiceHelper.VoiceItem voice) {
		int previousSpeakerId = sp1.getInt("active_kokoro_speaker", 31);
		sp1.edit().putInt("active_kokoro_speaker", voice.speakerId).apply();
		com.builtbydeep.sovra.KokoroEngine.getInstance().setActiveSpeakerId(voice.speakerId);
		binding.voiceNameTv.setText(voice.getFullLabel());
		if (previousSpeakerId != voice.speakerId) _forceResetToIdle();
	}

	private void updateEdgeTtsFilters(boolean isEdgeTts) {
		binding.textview67.setText(isEdgeTts ? "CLOUD VOICE MODEL" : "ONNX VOICE MODEL");
		if (!isEdgeTts) return;

		binding.kokoroVoiceFilters.setVisibility(android.view.View.VISIBLE);
		binding.voiceInfoCard.setVisibility(android.view.View.GONE);
		binding.opneDropdown.setVisibility(android.view.View.GONE);
		java.util.ArrayList<String> regionLocales = new java.util.ArrayList<>();
		java.util.List<String> preferredLocales = java.util.Arrays.asList(
				"hi-IN", "en-IN", "en-US", "en-GB", "ja-JP");
		for (String locale : preferredLocales) {
			if (!regionLocales.contains(locale)
					&& (edgeVoices == null || getEdgeVoicesForLocale(locale).size() > 0)) {
				regionLocales.add(locale);
			}
		}
		if (edgeVoices != null) {
			for (com.builtbydeep.sovra.EdgeTtsClient.Voice voice : edgeVoices) {
				if (!regionLocales.contains(voice.locale)) regionLocales.add(voice.locale);
			}
		}
		if (regionLocales.isEmpty()) regionLocales.addAll(preferredLocales);
		java.util.ArrayList<String> regionLabels = new java.util.ArrayList<>();
		for (String locale : regionLocales) {
			regionLabels.add(com.builtbydeep.sovra.EdgeTtsClient.formatLocaleDisplayName(locale));
		}
		String selectedLocale = sp1.getString("edge_locale", "hi-IN");
		int selectedRegion = Math.max(0, regionLocales.indexOf(selectedLocale));

		suppressEdgeFilterCallbacks = true;
		binding.kokoroRegionSpinner.setOnItemSelectedListener(null);
		binding.kokoroVoiceSpinner.setOnItemSelectedListener(null);
		binding.kokoroRegionSpinner.setAdapter(createKokoroSpinnerAdapter(regionLabels));
		binding.kokoroRegionSpinner.setSelection(selectedRegion, false);
		bindEdgeVoices(regionLocales.get(selectedRegion),
				sp1.getString("edge_voice", "hi-IN-SwaraNeural"));
		suppressEdgeFilterCallbacks = false;

		binding.kokoroRegionSpinner.setOnItemSelectedListener(
				new android.widget.AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view,
					int position, long id) {
				if (suppressEdgeFilterCallbacks || position < 0 || position >= regionLocales.size()) return;
				bindEdgeVoices(regionLocales.get(position), sp1.getString("edge_voice", ""));
			}

			@Override
			public void onNothingSelected(android.widget.AdapterView<?> parent) {}
		});
		binding.kokoroVoiceSpinner.setOnItemSelectedListener(
				new android.widget.AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view,
					int position, long id) {
				if (suppressEdgeFilterCallbacks || position < 0) return;
				java.util.List<com.builtbydeep.sovra.EdgeTtsClient.Voice> visible =
						getEdgeVoicesForLocale(sp1.getString("edge_locale", "hi-IN"));
				if (position >= visible.size()) return;
				com.builtbydeep.sovra.EdgeTtsClient.Voice voice = visible.get(position);
				String previousVoice = sp1.getString("edge_voice", "");
				sp1.edit().putString("edge_voice", voice.name)
						.putString("edge_locale", voice.locale)
						.putString("edge_voice_gender", voice.gender).apply();
				binding.voiceNameTv.setText(voice.displayName());
				if (!previousVoice.equals(voice.name)) _forceResetToIdle();
			}

			@Override
			public void onNothingSelected(android.widget.AdapterView<?> parent) {}
		});

		if (edgeVoicesFetched) return;
		edgeVoicesFetched = true;
		new Thread(() -> {
			java.util.List<com.builtbydeep.sovra.EdgeTtsClient.Voice> fetched =
					com.builtbydeep.sovra.EdgeTtsClient.fetchVoices();
			edgeVoices = fetched;
			if (getActivity() == null) return;
			getActivity().runOnUiThread(() -> {
				if (!isAdded() || binding == null || !com.builtbydeep.sovra.EdgeTtsClient.MODEL_TYPE
						.equalsIgnoreCase(sp1.getString("active_model_type", ""))) return;
				String locale = sp1.getString("edge_locale", "hi-IN");
				bindEdgeVoices(locale, sp1.getString("edge_voice", ""));
			});
		}, "edge-tts-voices").start();
	}

	private java.util.List<com.builtbydeep.sovra.EdgeTtsClient.Voice> getEdgeVoicesForLocale(
			String locale) {
		java.util.ArrayList<com.builtbydeep.sovra.EdgeTtsClient.Voice> matches =
				new java.util.ArrayList<>();
		java.util.LinkedHashSet<String> seenNames = new java.util.LinkedHashSet<>();
		for (com.builtbydeep.sovra.EdgeTtsClient.Voice voice : edgeVoices) {
			String key = voice.name.toLowerCase(java.util.Locale.ROOT);
			if (locale.equalsIgnoreCase(voice.locale) && seenNames.add(key)) matches.add(voice);
		}
		return matches;
	}

	private void updateCloudTtsFilters(boolean isCloudTts) {
		if (!isCloudTts || binding == null) return;
		String providerId = cloudProviderFromType(sp1.getString("active_model_type", ""));
		if (providerId.isEmpty()) return;
		String modelId = sp1.getString(com.builtbydeep.sovra.CloudTtsClients
						.getSelectedModelPreference(providerId),
				com.builtbydeep.sovra.CloudTtsClients.defaultModel(providerId));
		String apiKey = sp1.getString(com.builtbydeep.sovra.CloudTtsClients
				.getApiKeyPreference(providerId), "").trim();
		binding.textview67.setText("CLOUD VOICE MODEL");
		binding.supertonicControlsLayout.setVisibility(android.view.View.GONE);
		binding.voiceInfoCard.setVisibility(android.view.View.GONE);
		binding.opneDropdown.setVisibility(android.view.View.GONE);
		binding.kokoroVoiceFilters.setVisibility(android.view.View.VISIBLE);
		if (!com.builtbydeep.sovra.CloudTtsClients.RIME.equals(providerId) && !com.builtbydeep.sovra.ExtendedCloudProviders.isKeyless(providerId) && apiKey.isEmpty()) {
			binding.voiceNameTv.setText("Add API key in Settings");
			binding.kokoroRegionSpinner.setAdapter(createKokoroSpinnerAdapter(
					java.util.Collections.singletonList("API key required")));
			binding.kokoroVoiceSpinner.setAdapter(createKokoroSpinnerAdapter(
					java.util.Collections.singletonList("API key required")));
			return;
		}
		String cacheKey = providerId + "|" + modelId + "|" + apiKey.hashCode();
		if (cacheKey.equals(cloudVoiceCacheKey) && !cloudVoices.isEmpty()) {
			bindCloudVoices(providerId, modelId, cloudVoices);
			return;
		}
		if (cloudVoicesFetched) return;
		cloudVoicesFetched = true;
		new Thread(() -> {
			try {
				com.builtbydeep.sovra.CloudTtsProvider provider =
						com.builtbydeep.sovra.CloudTtsClients.get(providerId);
				java.util.List<com.builtbydeep.sovra.CloudTtsProvider.Voice> fetched =
						com.builtbydeep.sovra.CloudTtsClients.filterVoicesForModel(
								providerId, modelId, provider.fetchVoices(apiKey));
				cloudVoices = fetched;
				cloudVoiceCacheKey = cacheKey;
				cloudVoicesFetched = false;
				if (getActivity() == null) return;
				getActivity().runOnUiThread(() -> {
					if (binding == null || !isAdded()
							|| !("cloud-" + providerId).equalsIgnoreCase(
									sp1.getString("active_model_type", ""))
							|| !modelId.equals(sp1.getString(
									com.builtbydeep.sovra.CloudTtsClients
											.getSelectedModelPreference(providerId), modelId))) return;
					String currentKey = sp1.getString(com.builtbydeep.sovra.CloudTtsClients
							.getApiKeyPreference(providerId), "").trim();
					if (!cacheKey.equals(providerId + "|" + modelId + "|" + currentKey.hashCode())) {
						updateCloudTtsFilters(true);
						return;
					}
					if (fetched.isEmpty()) {
						binding.voiceNameTv.setText("No compatible voices for this model");
						return;
					}
					bindCloudVoices(providerId, modelId, fetched);
				});
			} catch (java.io.IOException error) {
				cloudVoicesFetched = false;
				android.util.Log.e("GenerateFragmentActivity",
						"Unable to fetch " + providerId + " voices.", error);
				if (getActivity() != null) getActivity().runOnUiThread(() -> {
					if (binding != null && isAdded()
							&& ("cloud-" + providerId).equalsIgnoreCase(
									sp1.getString("active_model_type", ""))) {
						binding.voiceNameTv.setText("Voice list unavailable");
						com.google.android.material.snackbar.Snackbar.make(binding.getRoot(),
								"Voice list unavailable: " + error.getMessage(),
								com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show();
					}
				});
			}
		}, "cloud-tts-voices").start();
	}

	private void bindCloudVoices(String providerId, String modelId,
								 java.util.List<com.builtbydeep.sovra.CloudTtsProvider.Voice> voices) {
		java.util.LinkedHashMap<String, java.util.List<com.builtbydeep.sovra.CloudTtsProvider.Voice>> groups =
				new java.util.LinkedHashMap<>();
		for (com.builtbydeep.sovra.CloudTtsProvider.Voice voice : voices) {
			String groupKey = voice.locale + "|" + voice.region;
			groups.computeIfAbsent(groupKey, ignored -> new java.util.ArrayList<>()).add(voice);
		}
		if (groups.isEmpty()) return;
		java.util.ArrayList<String> regionKeys = new java.util.ArrayList<>(groups.keySet());
		java.util.ArrayList<String> labels = new java.util.ArrayList<>();
		for (String key : regionKeys) {
			String[] pieces = key.split("\\|", 2);
			String language = java.util.Locale.forLanguageTag(pieces[0])
					.getDisplayLanguage(java.util.Locale.ENGLISH);
			String region = pieces.length > 1 && !pieces[1].isEmpty() ? pieces[1] : language;
			labels.add(region + " (" + pieces[0] + ")");
		}
		String regionPreference = "cloud_filter_region_" + providerId;
		String savedGroup = sp1.getString(regionPreference, "");
		int selectedRegion = regionKeys.indexOf(savedGroup);
		if (selectedRegion < 0) {
			String preferredVoice = sp1.getString("cloud_voice_" + providerId, "");
			for (int i = 0; i < regionKeys.size(); i++) {
				for (com.builtbydeep.sovra.CloudTtsProvider.Voice voice : groups.get(regionKeys.get(i))) {
					if (voice.id.equals(preferredVoice)) {
						selectedRegion = i;
						break;
					}
				}
				if (selectedRegion >= 0) break;
			}
		}
		if (selectedRegion < 0) selectedRegion = 0;
		sp1.edit().putString(regionPreference, regionKeys.get(selectedRegion)).apply();
		suppressCloudFilterCallbacks = true;
		binding.kokoroRegionSpinner.setOnItemSelectedListener(null);
		binding.kokoroVoiceSpinner.setOnItemSelectedListener(null);
		binding.kokoroRegionSpinner.setAdapter(createKokoroSpinnerAdapter(labels));
		binding.kokoroRegionSpinner.setSelection(selectedRegion, false);
		bindCloudVoiceOptions(providerId, groups.get(regionKeys.get(selectedRegion)));
		suppressCloudFilterCallbacks = false;
		binding.kokoroRegionSpinner.setOnItemSelectedListener(
				new android.widget.AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view,
									   int position, long id) {
				if (suppressCloudFilterCallbacks || position < 0 || position >= regionKeys.size()) return;
				sp1.edit().putString(regionPreference, regionKeys.get(position)).apply();
				bindCloudVoiceOptions(providerId, groups.get(regionKeys.get(position)));
			}
			@Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
		});
	}

	private void bindCloudVoiceOptions(String providerId,
									   java.util.List<com.builtbydeep.sovra.CloudTtsProvider.Voice> voices) {
		java.util.ArrayList<String> labels = new java.util.ArrayList<>();
		String selectedVoice = sp1.getString("cloud_voice_" + providerId, "");
		int selectedPosition = 0;
		for (int i = 0; i < voices.size(); i++) {
			com.builtbydeep.sovra.CloudTtsProvider.Voice voice = voices.get(i);
			labels.add(voice.displayName());
			if (voice.id.equals(selectedVoice)) selectedPosition = i;
		}
		if (voices.isEmpty()) {
			binding.voiceNameTv.setText("No compatible voices");
			return;
		}
		com.builtbydeep.sovra.CloudTtsProvider.Voice voice = voices.get(selectedPosition);
		if (!voice.id.equals(selectedVoice)) {
			sp1.edit().putString("cloud_voice_" + providerId, voice.id)
					.putString("cloud_locale_" + providerId, voice.locale)
					.putString("cloud_region_" + providerId, voice.region).apply();
		}
		binding.voiceNameTv.setText(voice.displayName());
		suppressCloudFilterCallbacks = true;
		binding.kokoroVoiceSpinner.setAdapter(createKokoroSpinnerAdapter(labels));
		binding.kokoroVoiceSpinner.setSelection(selectedPosition, false);
		suppressCloudFilterCallbacks = false;
		binding.kokoroVoiceSpinner.setOnItemSelectedListener(
				new android.widget.AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view,
									   int position, long id) {
				if (suppressCloudFilterCallbacks || position < 0 || position >= voices.size()) return;
				com.builtbydeep.sovra.CloudTtsProvider.Voice selected = voices.get(position);
				String previous = sp1.getString("cloud_voice_" + providerId, "");
				sp1.edit().putString("cloud_voice_" + providerId, selected.id)
						.putString("cloud_locale_" + providerId, selected.locale)
						.putString("cloud_region_" + providerId, selected.region)
						.putString("active_language", selected.locale).apply();
				binding.voiceNameTv.setText(selected.displayName());
				if (!previous.equals(selected.id)) {
					_forceResetToIdle();
				}
			}
			@Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
		});
	}

	private static String cloudProviderFromType(String modelType) {
		if (modelType == null) return "";
		String normalized = modelType.toLowerCase(java.util.Locale.ROOT);
		if (normalized.equals("cloud-rime")) return com.builtbydeep.sovra.CloudTtsClients.RIME;
		if (normalized.equals("cloud-speechify")) return com.builtbydeep.sovra.CloudTtsClients.SPEECHIFY;
		if (normalized.equals("cloud-elevenlabs")) return com.builtbydeep.sovra.CloudTtsClients.ELEVENLABS;
		if (normalized.equals("cloud-witai")) return com.builtbydeep.sovra.CloudTtsClients.WITAI;
                if (normalized.equals("cloud-capcut")) return com.builtbydeep.sovra.CloudTtsClients.CAPCUT;
                if (normalized.equals("cloud-fish")) return com.builtbydeep.sovra.CloudTtsClients.FISH;
                if (normalized.equals("cloud-groq")) return com.builtbydeep.sovra.CloudTtsClients.GROQ;
                if (normalized.equals("cloud-cartesia")) return com.builtbydeep.sovra.CloudTtsClients.CARTESIA;
                if (normalized.equals("cloud-gradium")) return com.builtbydeep.sovra.CloudTtsClients.GRADIUM;
                if (normalized.equals("cloud-cloudflare")) return com.builtbydeep.sovra.CloudTtsClients.CLOUDFLARE;
		return "";
	}

	private void bindEdgeVoices(String locale, String preferredVoice) {
		java.util.List<com.builtbydeep.sovra.EdgeTtsClient.Voice> voices =
				getEdgeVoicesForLocale(locale);
		if (voices.isEmpty()) return;
		java.util.ArrayList<String> labels = new java.util.ArrayList<>();
		int selectedPosition = 0;
		for (int i = 0; i < voices.size(); i++) {
			com.builtbydeep.sovra.EdgeTtsClient.Voice voice = voices.get(i);
			labels.add(voice.displayName());
			if (voice.name.equals(preferredVoice)) selectedPosition = i;
		}
		if (!voices.get(selectedPosition).name.equals(preferredVoice)) {
			com.builtbydeep.sovra.EdgeTtsClient.Voice selected = voices.get(selectedPosition);
			sp1.edit().putString("edge_voice", selected.name)
					.putString("edge_locale", selected.locale)
					.putString("edge_voice_gender", selected.gender).apply();
			binding.voiceNameTv.setText(selected.displayName());
		}
		suppressEdgeFilterCallbacks = true;
		binding.kokoroVoiceSpinner.setAdapter(createKokoroSpinnerAdapter(labels));
		binding.kokoroVoiceSpinner.setSelection(selectedPosition, false);
		suppressEdgeFilterCallbacks = false;
	}

	private boolean isNetworkAvailable() {
		android.net.ConnectivityManager manager = (android.net.ConnectivityManager)
				requireContext().getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
		if (manager == null) return false;
		android.net.Network network = manager.getActiveNetwork();
		android.net.NetworkCapabilities capabilities = network == null
				? null : manager.getNetworkCapabilities(network);
		return capabilities != null && capabilities.hasCapability(
				android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET);
	}

	@Override
	public void onResume() {
		super.onResume();
		String activeModel = sp1.getString("active_model", "");
		String activeModelType = sp1.getString("active_model_type", "vits");
		int activeSpeakerCount = sp1.getInt("active_speaker_count", 1);
		boolean isKokoro = _isKokoroModel(activeModelType);
		boolean isSupertonic = activeModelType.equalsIgnoreCase("supertonic");
		boolean isEdgeTts = com.builtbydeep.sovra.EdgeTtsClient.MODEL_TYPE
				.equalsIgnoreCase(activeModelType);
		boolean isCloudTts = !cloudProviderFromType(activeModelType).isEmpty();
		if (activeModel.isEmpty()) {
			binding.voiceNameTv.setText("No Model Selected");
			binding.voiceNameTv.setTextColor(android.graphics.Color.parseColor("#EF4444"));
			binding.opneDropdown.setVisibility(android.view.View.GONE);
		} else {
			if (isKokoro || isSupertonic || activeSpeakerCount > 1) {
				binding.opneDropdown.setVisibility(android.view.View.VISIBLE);
			} else {
				binding.opneDropdown.setVisibility(android.view.View.GONE);
			}
			if (isEdgeTts) {
				binding.voiceNameTv.setText(com.builtbydeep.sovra.EdgeTtsClient.formatDisplayName(
						sp1.getString("edge_voice", "hi-IN-SwaraNeural"),
						sp1.getString("edge_voice_gender", "Female")));
			} else if (isCloudTts) {
				String providerId = cloudProviderFromType(activeModelType);
				binding.voiceNameTv.setText(sp1.getString("cloud_voice_" + providerId,
						"").isEmpty() ? "Select cloud voice" : sp1.getString(
								"cloud_voice_" + providerId, ""));
			} else if (isSupertonic) {
				int speakerId = sp1.getInt("active_supertonic_speaker", 0);
				com.builtbydeep.sovra.SupertonicEngine.getInstance().setActiveSpeakerId(speakerId);
				binding.voiceNameTv.setText(getSupertonicVoiceProfile(speakerId));
			} else if (isKokoro) {
				try {
					int savedSpeakerId = sp1.getInt("active_kokoro_speaker", 31);
					com.builtbydeep.sovra.KokoroEngine.getInstance().setActiveSpeakerId(savedSpeakerId);
					String voiceName = _getKokoroVoiceName(savedSpeakerId);
					binding.voiceNameTv.setText((voiceName != null && !voiceName.isEmpty()) ? voiceName : "Kokoro Voice");
				} catch (Throwable t) {
					binding.voiceNameTv.setText("Kokoro Voice");
				}
			} else {
				String piperLang = "Unknown";
				String piperGender = "Unknown";
				String modelLabel = "Piper";
				try {
					String modelsDataRaw = sp1.getString("models_data", "[]");
					if (!modelsDataRaw.equals("[]")) {
						java.util.ArrayList<java.util.HashMap<String, Object>> mList =
						new com.google.gson.Gson().fromJson(modelsDataRaw,
						new com.google.gson.reflect.TypeToken<java.util.ArrayList<java.util.HashMap<String, Object>>>(){}.getType());
						if (mList != null) {
							for (java.util.HashMap<String, Object> model : mList) {
								String onnxPath = model.containsKey("onnx_path") && model.get("onnx_path") != null ? model.get("onnx_path").toString() : "";
								if (activeModel.equals(onnxPath)) {
									if (model.containsKey("language") && model.get("language") != null) {
										piperLang = model.get("language").toString().trim();
									}
									if (model.containsKey("gender") && model.get("gender") != null) {
										piperGender = model.get("gender").toString().trim();
									}
									if (model.containsKey("type") && model.get("type").toString().contains("MMS")) {
										modelLabel = "MMS";
									}
									break;
								}
							}
						}
					}
				} catch (Exception e) {}
				if (!piperLang.equals("Unknown") && piperLang.length() > 0) {
					piperLang = piperLang.substring(0, 1).toUpperCase() + piperLang.substring(1).toLowerCase();
				}
				if (!piperGender.equals("Unknown") && piperGender.length() > 0) {
					piperGender = piperGender.substring(0, 1).toUpperCase() + piperGender.substring(1).toLowerCase();
				}
				if (activeSpeakerCount > 1) {
					int currentPiperId = sp1.getInt("active_piper_speaker", 0);
					binding.voiceNameTv.setText(modelLabel + " • Speaker " + currentPiperId + " • " + piperLang);
				} else {
					binding.voiceNameTv.setText(modelLabel + " • " + piperGender + " • " + piperLang);
				}
			}
			binding.voiceNameTv.setTextColor(android.graphics.Color.WHITE);
		}
		if (listPopupWindow != null) {
			java.util.List<String> newVoiceNames = new java.util.ArrayList<>();
			if (isKokoro) {
				java.util.List<com.builtbydeep.sovra.KokoroVoiceHelper.VoiceItem> allVoices = _getKokoroVoices();
				for (com.builtbydeep.sovra.KokoroVoiceHelper.VoiceItem vItem : allVoices) {
					try {
						String label = vItem.getFullLabel();
						newVoiceNames.add((label != null && !label.isEmpty()) ? label : "Unknown Voice");
					} catch (Throwable t) {
						newVoiceNames.add("Unknown Voice");
					}
				}
			} else if (isSupertonic || activeSpeakerCount > 1) {
				for (int i = 0; i < activeSpeakerCount; i++) {
					newVoiceNames.add(isSupertonic ? getSupertonicVoiceProfile(i) : "Speaker " + i);
				}
			}
			if (voiceAdapter != null) {
				voiceAdapter.clear();
				voiceAdapter.addAll(newVoiceNames);
				voiceAdapter.notifyDataSetChanged();
			}
		}
		_updateSupertonicLanguageSelector();
		updateKokoroVoiceFilters(isKokoro, sp1.getInt("active_kokoro_speaker", 31));
		updateEdgeTtsFilters(isEdgeTts);
		updateCloudTtsFilters(isCloudTts);
		String currentSettingsState = sp3.getFloat("voice_speed", 1.0f) + "_" +
		sp3.getFloat("voice_pitch", 1.0f) + "_" +
		sp3.getFloat("silence_scale", 0.2f) + "_" +
		sp3.getBoolean("smart_punct", false) + "_" +
		sp3.getBoolean("emotion_tags", false) + "_" +
		sp3.getBoolean("multi_speaker_tags", false) + "_" +
		sp3.getInt("voice_style", 1);
		boolean isSettingChanged = !lastSettingsState.isEmpty() && !currentSettingsState.equals(lastSettingsState);
		boolean isModelChanged = !currentUiModelPath.isEmpty() && !activeModel.equals(currentUiModelPath);
		if (isModelChanged || isSettingChanged) {
			_forceResetToIdle();
		}
		currentUiModelPath = activeModel;
		lastSettingsState = currentSettingsState;
		
	}
	
	
	@Override
	public void onStart() {
		super.onStart();
		try {
			// Check if Activity is not null and is an instance of MainActivity
			if (getActivity() != null && getActivity() instanceof MainActivity) {
				MainActivity mainActivity = (MainActivity) getActivity();
				if (mainActivity.sharedProcessText != null && !mainActivity.sharedProcessText.isEmpty()) {
					binding.etInput.setText(mainActivity.sharedProcessText);
					binding.etInput.setSelection(mainActivity.sharedProcessText.length());
					mainActivity.sharedProcessText = ""; 
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}
	private android.media.AudioTrack createEdgeStreamTrack(int sampleRate)
			throws java.io.IOException {
		int minBufferSize = android.media.AudioTrack.getMinBufferSize(sampleRate,
				android.media.AudioFormat.CHANNEL_OUT_MONO,
				android.media.AudioFormat.ENCODING_PCM_16BIT);
		if (minBufferSize <= 0) {
			throw new java.io.IOException("Unable to initialize audio playback.");
		}
		android.media.AudioTrack track = new android.media.AudioTrack.Builder()
				.setAudioAttributes(new android.media.AudioAttributes.Builder()
						.setUsage(android.media.AudioAttributes.USAGE_MEDIA)
						.setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
						.build())
				.setAudioFormat(new android.media.AudioFormat.Builder()
						.setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
						.setSampleRate(sampleRate)
						.setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO)
						.build())
				.setBufferSizeInBytes(Math.max(minBufferSize, sampleRate * 2))
				.setTransferMode(android.media.AudioTrack.MODE_STREAM)
				.build();
		if (track.getState() != android.media.AudioTrack.STATE_INITIALIZED) {
			track.release();
			throw new java.io.IOException("Unable to initialize audio playback.");
		}
		return track;
	}

	private void startEdgePlaybackMonitor(android.media.AudioTrack track, long playbackId,
	                                      long totalFrames, double totalSeconds) {
		long playedFrames = Integer.toUnsignedLong(track.getPlaybackHeadPosition());
		int width = binding.imgWaveform.getWidth() > 0 ? binding.imgWaveform.getWidth() : 800;
		float startX = (float) Math.min(1.0, (double) playedFrames / totalFrames) * width;
		binding.playheadLine.setTranslationX(startX);
		if (playheadAnimator != null) playheadAnimator.cancel();
		playheadAnimator = android.animation.ValueAnimator.ofFloat(startX, (float) width);
		playheadAnimator.setDuration(Math.max(0L,
				(long) ((totalFrames - Math.min(playedFrames, totalFrames))
						* 1000.0 / lastGeneratedSampleRate)));
		playheadAnimator.setInterpolator(new android.view.animation.LinearInterpolator());
		playheadAnimator.addUpdateListener(animator -> {
			float position = (float) animator.getAnimatedValue();
			binding.playheadLine.setTranslationX(position);
			double elapsed = position / width * totalSeconds;
			binding.tvDuration.setText(String.format(java.util.Locale.US, "%d:%02d / %d:%02d",
					(int) (elapsed / 60), (int) (elapsed % 60),
					(int) (totalSeconds / 60), (int) (totalSeconds % 60)));
		});
		playheadAnimator.start();

		new Thread(() -> {
			while (liveStreamTrack == track && edgePlaybackId == playbackId && !isCancelled) {
				long head = Integer.toUnsignedLong(track.getPlaybackHeadPosition());
				if (head >= totalFrames) break;
				try {
					Thread.sleep(100L);
				} catch (InterruptedException interrupted) {
					Thread.currentThread().interrupt();
					return;
				}
			}
			if (liveStreamTrack != track || edgePlaybackId != playbackId || isCancelled) return;
			if (getActivity() != null && isAdded()) {
				getActivity().runOnUiThread(() -> {
					if (liveStreamTrack != track || edgePlaybackId != playbackId) return;
					try {
						track.stop();
						track.release();
					} catch (IllegalStateException error) {
						android.util.Log.w("GenerateFragmentActivity",
								"Unable to release completed Edge TTS playback.", error);
					}
					liveStreamTrack = null;
					edgeStreamStarted = false;
					binding.imageview52.setImageResource(R.drawable.icon_play_circle);
					binding.textview92.setText("Play");
					binding.playheadLine.setTranslationX(0f);
					binding.tvDuration.setText(String.format(java.util.Locale.US, "0:00 / %d:%02d",
							(int) (totalSeconds / 60), (int) (totalSeconds % 60)));
					if (playheadAnimator != null) playheadAnimator.cancel();
					com.builtbydeep.sovra.system.VoxMediaController.getInstance(getContext())
							.hideNotification();
				});
			}
		}, "edge-playback-monitor").start();
	}

	private void startEdgeFilePlayback() {
		final java.io.File pcmFile = lastGeneratedPcmFile;
		if (pcmFile == null || !pcmFile.isFile() || lastGeneratedPcmBytes <= 0) return;
		final int sampleRate = lastGeneratedSampleRate;
		final long totalFrames = lastGeneratedPcmBytes / 2L;
		final double totalSeconds = (double) totalFrames / sampleRate;
		final long playbackId = ++edgePlaybackId;
		final int generationId = currentGenerationId;
		try {
			android.media.AudioTrack track = createEdgeStreamTrack(sampleRate);
			liveStreamTrack = track;
			edgeStreamStarted = true;
			if (audioManager != null && focusRequest != null) audioManager.requestAudioFocus(focusRequest);
			track.play();
			binding.imageview52.setImageResource(R.drawable.icon_pause_circle);
			binding.textview92.setText("Pause");
			com.builtbydeep.sovra.system.VoxMediaController.getInstance(getContext())
					.updatePlaybackState("Sovra Audio", "Playing...",
							com.builtbydeep.sovra.system.VoxMediaController.STATE_PLAYING, false);
			new Thread(() -> {
				try (java.io.BufferedInputStream input =
							 new java.io.BufferedInputStream(new java.io.FileInputStream(pcmFile))) {
					byte[] buffer = new byte[8192];
					int length;
					while ((length = input.read(buffer)) != -1) {
						int offset = 0;
						while (offset < length) {
							if (playbackId != edgePlaybackId || generationId != currentGenerationId
									|| liveStreamTrack != track) return;
							int written = track.write(buffer, offset, length - offset);
							if (written < 0) {
								throw new java.io.IOException("Unable to play saved Edge TTS audio.");
							}
							if (written == 0) Thread.sleep(10L);
							else offset += written;
						}
					}
					if (playbackId == edgePlaybackId && liveStreamTrack == track) {
						if (getActivity() != null && isAdded()) {
							getActivity().runOnUiThread(() -> {
								if (playbackId == edgePlaybackId && liveStreamTrack == track) {
									startEdgePlaybackMonitor(track, playbackId, totalFrames, totalSeconds);
								}
							});
						}
					}
				} catch (InterruptedException interrupted) {
					Thread.currentThread().interrupt();
				} catch (java.io.IOException error) {
					android.util.Log.e("GenerateFragmentActivity",
							"Unable to play saved Edge TTS audio.", error);
					if (liveStreamTrack == track && playbackId == edgePlaybackId) {
						try {
							track.stop();
							track.release();
						} catch (IllegalStateException releaseError) {
							android.util.Log.w("GenerateFragmentActivity",
									"Unable to release failed Edge TTS playback.", releaseError);
						}
						liveStreamTrack = null;
						edgeStreamStarted = false;
						if (getActivity() != null && isAdded()) {
							getActivity().runOnUiThread(() -> {
								binding.imageview52.setImageResource(R.drawable.icon_play_circle);
								binding.textview92.setText("Play");
								com.builtbydeep.sovra.system.VoxMediaController.getInstance(getContext())
										.hideNotification();
							});
						}
					}
				}
			}, "edge-file-playback").start();
		} catch (java.io.IOException error) {
			android.util.Log.e("GenerateFragmentActivity", "Unable to initialize Edge TTS playback.", error);
		}
	}

	public void _saveAudioAction() {
		// ─── SAVE BUTTON LOGIC ──────────────────────────────────────────────────────
		
		// Pehle check karenge ki audio generate hui hai ya nahi.
		if (!isAudioGeneratedForCurrentText
				|| (lastGeneratedPcmData == null && lastGeneratedPcmFile == null)) {
			if (getView() != null) {
				com.google.android.material.snackbar.Snackbar.make(getView(), "Please generate audio first!", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
				.setBackgroundTint(android.graphics.Color.parseColor("#EF4444"))
				.setTextColor(android.graphics.Color.WHITE)
				.show();
			}
			return;
		}
		
		// Yahan se aapka original save logic shuru hota hai (Bina kisi ads ke)
		int sampleRateToSave = lastGeneratedSampleRate > 0 ? lastGeneratedSampleRate : 22050;
		
		boolean saveMp3 = lastGeneratedMp3Data != null && lastGeneratedMp3Data.length > 0;
		String cleanFileName = "Vox_" + System.currentTimeMillis() + (saveMp3 ? ".mp3" : ".wav");
		String savedPath = saveMp3
				? com.builtbydeep.sovra.AudioHelper.saveMp3File(
						lastGeneratedMp3Data, cleanFileName, getContext())
				: lastGeneratedPcmFile != null
				? com.builtbydeep.sovra.AudioHelper.saveWavFile(
						lastGeneratedPcmFile, cleanFileName, sampleRateToSave, getContext())
				: com.builtbydeep.sovra.AudioHelper.saveWavFile(
						lastGeneratedPcmData, cleanFileName, sampleRateToSave, getContext());
		
		if (!savedPath.isEmpty()) {
			try {
				String libraryData = sp2.getString("library_list", "[]");
				java.util.ArrayList<java.util.HashMap<String, Object>> libList = new com.google.gson.Gson().fromJson(
				libraryData, new com.google.gson.reflect.TypeToken<java.util.ArrayList<java.util.HashMap<String, Object>>>(){}.getType()
				);
				
				if (libList == null) {
					libList = new java.util.ArrayList<>();
				}
				
				java.util.HashMap<String, Object> newItem = new java.util.HashMap<>();
				newItem.put("title", cleanFileName);
				
				// Extract the first sentence from the generated text for a clean title
				String rawText = (lastParams != null && lastParams.text != null) ? lastParams.text : lastGeneratedText;
				if (rawText == null || rawText.trim().isEmpty()) {
					rawText = binding.etInput.getText().toString().trim();
				}
				
				String displayTitle = rawText.trim();
				int dotIndex = displayTitle.indexOf('.');
				int newlineIndex = displayTitle.indexOf('\n');
				int endIndex = -1;
				
				if (dotIndex > 0 && newlineIndex > 0) {
					endIndex = Math.min(dotIndex, newlineIndex);
				} else if (dotIndex > 0) {
					endIndex = dotIndex;
				} else if (newlineIndex > 0) {
					endIndex = newlineIndex;
				}
				
				if (endIndex > 0) {
					displayTitle = displayTitle.substring(0, endIndex + 1).trim();
				} else if (displayTitle.length() > 50) {
					displayTitle = displayTitle.substring(0, 50).trim() + "...";
				}
				
				if (displayTitle.isEmpty()) {
					displayTitle = "Saved Audio";
				}
				
				newItem.put("text", displayTitle);
				newItem.put("full_text", rawText); 
				newItem.put("path", savedPath);
				
				// Exact duration calculation to prevent UI formatting bugs
				long audioBytes = lastGeneratedPcmFile != null
						? lastGeneratedPcmBytes : lastGeneratedPcmData.length;
				double totalSeconds = (audioBytes / 2.0) / sampleRateToSave;
				int min = (int) (totalSeconds / 60);
				int sec = (int) (totalSeconds % 60);
				String cleanDuration = String.format(java.util.Locale.US, "%d:%02d", min, sec);
				
				newItem.put("duration", cleanDuration);
				newItem.put("timestamp", String.valueOf(System.currentTimeMillis()));
				newItem.put("voice_name", binding.voiceNameTv.getText().toString());
				newItem.put("is_favorite", false);
				
				libList.add(0, newItem);
				sp2.edit().putString("library_list", new com.google.gson.Gson().toJson(libList)).apply();
				
				if (getView() != null) {
					com.google.android.material.snackbar.Snackbar.make(getView(), "Audio saved to Library!", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
					.setBackgroundTint(android.graphics.Color.parseColor("#4A9EFF"))
					.setTextColor(android.graphics.Color.WHITE)
					.show();
				}
			} catch (Exception e) {
				if (getView() != null) {
					com.google.android.material.snackbar.Snackbar.make(getView(), "Failed to update library data.", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
					.setBackgroundTint(android.graphics.Color.parseColor("#EF4444"))
					.setTextColor(android.graphics.Color.WHITE)
					.show();
				}
			}
		} else {
			if (getView() != null) {
				com.google.android.material.snackbar.Snackbar.make(getView(), "Failed to save audio file.", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
				.setBackgroundTint(android.graphics.Color.parseColor("#EF4444"))
				.setTextColor(android.graphics.Color.WHITE)
				.show();
			}
		}
		// ─── SAVE BUTTON LOGIC END ──────────────────────────────────────────────────
		
	}
	
	
	public void _forceResetToIdle() {
		boolean resetEdgeStream = edgeStreamStarted;
		if (sp1 != null && getContext() != null
				&& com.builtbydeep.sovra.CloudTtsClients.WITAI.equals(
						cloudProviderFromType(sp1.getString("active_model_type", "")))) {
			com.builtbydeep.sovra.CloudTtsClients.getWitAiProvider()
					.cancelAndClean(getContext().getApplicationContext());
		}
		// CRITICAL SIGSEGV FIX: Cancel native engines to stop C++ background generation immediately
		try {
			com.builtbydeep.sovra.VoiceEngine.getInstance().cancel();
			com.builtbydeep.sovra.KokoroEngine.getInstance().cancel();
			com.builtbydeep.sovra.SupertonicEngine.getInstance().cancel();
			edgeTtsClient.cancel();
		} catch (Throwable ignored) {}
		
		currentGenerationId++; 
		edgePlaybackId++;
		isCancelled = true;
		isGenerating = false;
		isEdgeTtsGenerating = false;
		isAudioGeneratedForCurrentText = false;
		lastParams = null;
		lastGeneratedPcmData = null;
		lastGeneratedMp3Data = null;
		if (lastGeneratedPcmFile != null) lastGeneratedPcmFile.delete();
		lastGeneratedPcmFile = null;
		if (activeEdgePcmFile != null) activeEdgePcmFile.delete();
		activeEdgePcmFile = null;
		lastGeneratedPcmBytes = 0;
		edgeStreamStarted = false;
		
		try {
			if (audioTrack != null) {
				audioTrack.stop();
				audioTrack.release();
				audioTrack = null;
			}
		} catch (Exception ignored) {}
		
		try {
			if (liveStreamTrack != null) {
				if (resetEdgeStream) liveStreamTrack.flush();
				liveStreamTrack.stop();
				liveStreamTrack.release();
				liveStreamTrack = null;
			}
		} catch (Exception ignored) {}
		
		if (playheadAnimator != null) playheadAnimator.cancel();
		
		binding.playheadLine.setTranslationX(0f);
		binding.layoutGeneratedState.setVisibility(android.view.View.GONE);
		binding.layoutIdleState.setVisibility(android.view.View.VISIBLE);
		binding.progressGenerating.setVisibility(android.view.View.GONE);
		binding.imageview65.setVisibility(android.view.View.VISIBLE);
		
		binding.textview92.setText("Play");
		binding.imageview52.setImageResource(R.drawable.icon_play_circle);
		binding.textview69.setText("READY TO SYNTHESIZE");
		binding.textview69.setTextColor(android.graphics.Color.parseColor("#94A3B8"));
		
	}
	
	
	public void _requestAudioFocus() {
		
		if (audioManager == null) return;
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
			if (focusRequest != null) audioManager.requestAudioFocus(focusRequest);
		} else {
			audioManager.requestAudioFocus(focusListener, android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.AUDIOFOCUS_GAIN);
		}
	}
	
	
	public void _toggleGeneratePlayback() {
		if (isGenerating && edgeStreamStarted) {
			_cancelGeneration();
			return;
		}
		if (edgeStreamStarted && liveStreamTrack != null) {
			android.media.AudioTrack track = liveStreamTrack;
			if (track.getPlayState() == android.media.AudioTrack.PLAYSTATE_PLAYING) {
				track.pause();
				binding.imageview52.setImageResource(R.drawable.icon_play_circle);
				binding.textview92.setText("Play");
				if (playheadAnimator != null) playheadAnimator.pause();
				com.builtbydeep.sovra.system.VoxMediaController.getInstance(getContext())
						.updatePlaybackState("Sovra Audio", "Paused",
								com.builtbydeep.sovra.system.VoxMediaController.STATE_PAUSED, false);
			} else {
				_requestAudioFocus();
				track.play();
				binding.imageview52.setImageResource(R.drawable.icon_pause_circle);
				binding.textview92.setText("Pause");
				if (playheadAnimator != null && playheadAnimator.isPaused()) {
					playheadAnimator.resume();
				}
				com.builtbydeep.sovra.system.VoxMediaController.getInstance(getContext())
						.updatePlaybackState("Sovra Audio", "Playing...",
								com.builtbydeep.sovra.system.VoxMediaController.STATE_PLAYING, false);
			}
			return;
		}
		if (isAudioGeneratedForCurrentText && lastGeneratedPcmFile != null) {
			if (liveStreamTrack != null
					&& liveStreamTrack.getPlayState() == android.media.AudioTrack.PLAYSTATE_PLAYING) {
				liveStreamTrack.pause();
				binding.imageview52.setImageResource(R.drawable.icon_play_circle);
				binding.textview92.setText("Play");
				if (playheadAnimator != null) playheadAnimator.pause();
				com.builtbydeep.sovra.system.VoxMediaController.getInstance(getContext())
						.updatePlaybackState("Sovra Audio", "Paused",
								com.builtbydeep.sovra.system.VoxMediaController.STATE_PAUSED, false);
			} else if (liveStreamTrack != null
					&& liveStreamTrack.getPlayState() == android.media.AudioTrack.PLAYSTATE_PAUSED) {
				_requestAudioFocus();
				liveStreamTrack.play();
				binding.imageview52.setImageResource(R.drawable.icon_pause_circle);
				binding.textview92.setText("Pause");
				if (playheadAnimator != null && playheadAnimator.isPaused()) {
					playheadAnimator.resume();
				}
				com.builtbydeep.sovra.system.VoxMediaController.getInstance(getContext())
						.updatePlaybackState("Sovra Audio", "Playing...",
								com.builtbydeep.sovra.system.VoxMediaController.STATE_PLAYING, false);
			} else {
				startEdgeFilePlayback();
			}
			return;
		}
		
		if (!isAudioGeneratedForCurrentText || audioTrack == null || lastGeneratedPcmData == null) return;
		
		int playState = audioTrack.getPlayState();
		int currentHead = audioTrack.getPlaybackHeadPosition();
		int totalFrames = lastGeneratedPcmData.length / 2;
		
		if (playState == android.media.AudioTrack.PLAYSTATE_PLAYING) {
			audioTrack.pause();
			binding.imageview52.setImageResource(R.drawable.icon_play_circle);
			binding.textview92.setText("Play");
			if (playheadAnimator != null) playheadAnimator.pause();
			
			com.builtbydeep.sovra.system.VoxMediaController.getInstance(getContext()).updatePlaybackState("Sovra Audio", "Paused", com.builtbydeep.sovra.system.VoxMediaController.STATE_PAUSED, false);
		} else {
			final double totalSeconds = (double) totalFrames / lastGeneratedSampleRate;
			final int _totalMin = (int)(totalSeconds / 60);
			final int _totalSec = (int)(totalSeconds % 60);
			final int w = binding.imgWaveform.getWidth() > 0 ? binding.imgWaveform.getWidth() : 800;
			
			// 🚀 FIX: Removed 'flush()' which was causing Silent Play. Added proper reset loop.
			if (currentHead >= totalFrames - 4000 || playState == android.media.AudioTrack.PLAYSTATE_STOPPED) {
				try {
					audioTrack.stop();
					audioTrack.reloadStaticData();
					audioTrack.setPlaybackHeadPosition(0);
				} catch (Exception ignored) {}
				
				binding.playheadLine.setTranslationX(0f);
				if (playheadAnimator != null) playheadAnimator.cancel();
				
				playheadAnimator = android.animation.ValueAnimator.ofFloat(0f, (float) w);
				playheadAnimator.setDuration((long) (totalSeconds * 1000));
				playheadAnimator.setInterpolator(new android.view.animation.LinearInterpolator());
				playheadAnimator.addUpdateListener(anim -> {
					float tx = (float) anim.getAnimatedValue();
					binding.playheadLine.setTranslationX(tx);
					double elapsed = (tx / w) * totalSeconds;
					binding.tvDuration.setText(String.format(java.util.Locale.US, "%d:%02d / %d:%02d",
					(int)(elapsed / 60), (int)(elapsed % 60), _totalMin, _totalSec));
				});
				
				playheadAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
					@Override
					public void onAnimationEnd(android.animation.Animator animation) {
						if (binding.playheadLine.getTranslationX() >= w - 10) {
							binding.imageview52.setImageResource(R.drawable.icon_play_circle);
							binding.textview92.setText("Play");
							binding.playheadLine.setTranslationX(0f);
							binding.tvDuration.setText(String.format(java.util.Locale.US,
							"0:00 / %d:%02d", _totalMin, _totalSec));
							try {
								if (audioTrack != null) {
									audioTrack.stop();
									audioTrack.reloadStaticData();
									audioTrack.setPlaybackHeadPosition(0);
								}
							} catch (Exception ignored) {}
							
							com.builtbydeep.sovra.system.VoxMediaController.getInstance(getContext()).hideNotification();
						}
					}
				});
			}
			
			_requestAudioFocus();
			audioTrack.play();
			binding.imageview52.setImageResource(R.drawable.icon_pause_circle);
			binding.textview92.setText("Pause");
			
			com.builtbydeep.sovra.system.VoxMediaController.getInstance(getContext()).updatePlaybackState("Sovra Audio", "Playing...", com.builtbydeep.sovra.system.VoxMediaController.STATE_PLAYING, false);
			
			if (playheadAnimator != null) {
				if (playheadAnimator.isPaused()) {
					playheadAnimator.resume();
				} else {
					playheadAnimator.start();
				}
			}
		}
	}
	
	
	public void _cancelGeneration() {
		if (isGenerating && !isCancelled) {
			boolean cancelEdgeStream = isEdgeTtsGenerating;
			com.builtbydeep.sovra.EdgeTtsClient clientToCancel = edgeTtsClient;
			isCancelled = true;
			isGenerating = false;
			if (com.builtbydeep.sovra.CloudTtsClients.WITAI.equals(
					cloudProviderFromType(sp1.getString("active_model_type", "")))) {
				com.builtbydeep.sovra.CloudTtsClients.getWitAiProvider()
						.cancelAndClean(requireContext().getApplicationContext());
			}
			isEdgeTtsGenerating = false;
			if (cancelEdgeStream) {
				currentGenerationId++;
				edgePlaybackId++;
				clientToCancel.cancel();
				if (activeEdgePcmFile != null) activeEdgePcmFile.delete();
				activeEdgePcmFile = null;
			}
			new Thread(() -> {
				try { com.builtbydeep.sovra.VoiceEngine.getInstance().cancel(); } catch (Exception ignored) {}
				try { com.builtbydeep.sovra.KokoroEngine.getInstance().cancel(); } catch (Exception ignored) {}
				try { com.builtbydeep.sovra.SupertonicEngine.getInstance().cancel(); } catch (Exception ignored) {}
				if (!cancelEdgeStream) clientToCancel.cancel();
			}).start();
			
			// LiveStream track (Instant Silence)
			try {
				if (liveStreamTrack != null) {
					liveStreamTrack.pause();
					liveStreamTrack.flush();
					liveStreamTrack.stop();
					if (cancelEdgeStream) {
						liveStreamTrack.release();
						liveStreamTrack = null;
					}
				}
			} catch (Exception ignored) {}
			if (cancelEdgeStream) edgeStreamStarted = false;
			
			if (binding != null) {
				try {
					if (cancelEdgeStream) {
						binding.textview92.setText("Play");
						binding.imageview52.setImageResource(R.drawable.icon_play_circle);
						binding.textview69.setText("READY TO SYNTHESIZE");
						binding.progressGenerating.setVisibility(android.view.View.GONE);
						binding.imageview65.setVisibility(android.view.View.VISIBLE);
						binding.btnGenerate.setAlpha(1.0f);
						binding.btnGenerate.setEnabled(true);
					} else {
						binding.textview92.setText("Canceling...");
						binding.btnGenerate.setAlpha(0.5f);
						binding.btnGenerate.setEnabled(false);
					}
				} catch (Exception ignored) {}
			}
			
			try {
				com.builtbydeep.sovra.system.VoxMediaController.getInstance(getContext()).hideNotification();
			} catch (Exception ignored) {}
		}
		
	}
	
}
