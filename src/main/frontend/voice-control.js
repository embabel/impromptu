/**
 * Voice control integration using OpenAI APIs.
 * - Speech-to-text: OpenAI Whisper (with browser fallback)
 * - Text-to-speech: OpenAI TTS (with browser fallback)
 */

// Browser speech recognition (fallback)
const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
const speechSynthesis = window.speechSynthesis;

// Recording state
let mediaRecorder = null;
let audioChunks = [];
let isListening = false;
let currentComponent = null;
let audioStream = null;

/**
 * Start listening for speech input using microphone recording.
 * Records audio and sends to server for Whisper transcription.
 */
window.startListening = async function(component) {
    currentComponent = component;

    if (isListening) {
        // Already listening, stop first
        window.stopListening();
        return;
    }

    try {
        // Request microphone access
        audioStream = await navigator.mediaDevices.getUserMedia({ audio: true });

        // Create MediaRecorder
        const mimeType = MediaRecorder.isTypeSupported('audio/webm') ? 'audio/webm' : 'audio/mp4';
        mediaRecorder = new MediaRecorder(audioStream, { mimeType });
        audioChunks = [];

        mediaRecorder.ondataavailable = (event) => {
            if (event.data.size > 0) {
                audioChunks.push(event.data);
            }
        };

        mediaRecorder.onstop = async () => {
            isListening = false;

            // Stop all tracks
            if (audioStream) {
                audioStream.getTracks().forEach(track => track.stop());
                audioStream = null;
            }

            if (audioChunks.length === 0) {
                console.warn('STT: No audio recorded');
                if (currentComponent) {
                    currentComponent.$server.onListeningStopped();
                }
                return;
            }

            // Create blob from recorded chunks
            const audioBlob = new Blob(audioChunks, { type: mediaRecorder.mimeType });
            console.log('STT: Recorded', audioBlob.size, 'bytes of audio');

            // Transcribe using server
            await transcribeAudio(audioBlob);
        };

        mediaRecorder.onerror = (event) => {
            console.error('MediaRecorder error:', event.error);
            isListening = false;
            if (audioStream) {
                audioStream.getTracks().forEach(track => track.stop());
                audioStream = null;
            }
            if (currentComponent) {
                currentComponent.$server.onSpeechError('Recording failed');
                currentComponent.$server.onListeningStopped();
            }
        };

        // Start recording
        mediaRecorder.start();
        isListening = true;
        console.log('STT: Recording started');

        if (currentComponent) {
            currentComponent.$server.onListeningStarted();
        }

    } catch (error) {
        console.error('Failed to start recording:', error);
        isListening = false;

        if (error.name === 'NotAllowedError') {
            if (currentComponent) {
                currentComponent.$server.onSpeechError('Microphone access denied');
            }
        } else {
            if (currentComponent) {
                currentComponent.$server.onSpeechError(error.message);
            }
        }
    }
};

/**
 * Stop listening and transcribe the recorded audio.
 */
window.stopListening = function() {
    if (mediaRecorder && isListening) {
        console.log('STT: Stopping recording');
        mediaRecorder.stop();
    }
};

/**
 * Transcribe audio using server-side Whisper API.
 * Falls back to browser speech recognition if server fails.
 */
async function transcribeAudio(audioBlob) {
    try {
        const formData = new FormData();
        formData.append('audio', audioBlob, 'recording.webm');

        console.log('STT: Sending audio to server for transcription');

        const response = await fetch('/api/stt/transcribe', {
            method: 'POST',
            body: formData
        });

        if (response.ok) {
            const result = await response.json();
            console.log('STT: Using OpenAI Whisper - transcribed:', result.text);

            if (result.text && result.text.trim()) {
                if (currentComponent) {
                    currentComponent.$server.onSpeechResult(result.text.trim(), true);
                }
            } else {
                console.log('STT: No speech detected in audio');
            }
        } else if (response.status === 503) {
            console.warn('STT: Server not configured, falling back to browser recognition');
            fallbackToBrowserRecognition();
        } else {
            const error = await response.json().catch(() => ({ error: 'Unknown error' }));
            console.warn('STT: Server error:', error.error);
            if (currentComponent) {
                currentComponent.$server.onSpeechError(error.error || 'Transcription failed');
            }
        }
    } catch (error) {
        console.error('STT: Transcription request failed:', error);
        if (currentComponent) {
            currentComponent.$server.onSpeechError('Transcription failed');
        }
    }

    if (currentComponent) {
        currentComponent.$server.onListeningStopped();
    }
}

/**
 * Fallback to browser speech recognition if server STT is unavailable.
 */
function fallbackToBrowserRecognition() {
    if (!SpeechRecognition) {
        console.warn('STT: Browser speech recognition not supported');
        if (currentComponent) {
            currentComponent.$server.onSpeechError('Speech recognition not available');
            currentComponent.$server.onListeningStopped();
        }
        return;
    }

    console.log('STT: Using browser speech recognition (fallback)');

    const recognition = new SpeechRecognition();
    recognition.continuous = false;
    recognition.interimResults = false;
    recognition.lang = 'en-US';

    recognition.onresult = (event) => {
        const transcript = event.results[0][0].transcript;
        if (currentComponent) {
            currentComponent.$server.onSpeechResult(transcript, true);
        }
    };

    recognition.onerror = (event) => {
        console.error('Browser speech recognition error:', event.error);
        if (currentComponent) {
            currentComponent.$server.onSpeechError(event.error);
        }
    };

    recognition.onend = () => {
        if (currentComponent) {
            currentComponent.$server.onListeningStopped();
        }
    };

    try {
        recognition.start();
    } catch (e) {
        console.error('Failed to start browser recognition:', e);
        if (currentComponent) {
            currentComponent.$server.onSpeechError(e.message);
            currentComponent.$server.onListeningStopped();
        }
    }
}

// Current audio element for playback
let currentAudio = null;

/**
 * Speak text using server-side TTS (OpenAI).
 * Falls back to browser speech synthesis if server TTS fails.
 */
window.speakText = async function(text, component) {
    // Stop any current playback
    stopCurrentAudio();

    if (!text || text.trim() === '') {
        return;
    }

    try {
        // Try server-side TTS first
        const response = await fetch('/api/tts/synthesize', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({ text: text })
        });

        if (response.ok) {
            console.log('TTS: Using OpenAI server TTS');
            const audioBlob = await response.blob();
            const audioUrl = URL.createObjectURL(audioBlob);

            currentAudio = new Audio(audioUrl);

            currentAudio.onplay = () => {
                if (component) {
                    component.$server.onSpeakingStarted();
                }
            };

            currentAudio.onended = () => {
                URL.revokeObjectURL(audioUrl);
                currentAudio = null;
                if (component) {
                    component.$server.onSpeakingStopped();
                }
            };

            currentAudio.onerror = (e) => {
                console.error('Audio playback error:', e);
                URL.revokeObjectURL(audioUrl);
                currentAudio = null;
                if (component) {
                    component.$server.onSpeakingStopped();
                }
                // Fall back to browser TTS
                console.warn('TTS: Falling back to browser TTS - audio playback failed');
                speakWithBrowserTts(text, component);
            };

            await currentAudio.play();
        } else if (response.status === 503) {
            // TTS not configured, fall back to browser
            console.warn('TTS: Falling back to browser TTS - server returned 503 (OPENAI_API_KEY not configured)');
            speakWithBrowserTts(text, component);
        } else if (response.status === 401 || response.status === 403) {
            console.warn('TTS: Falling back to browser TTS - not authenticated (status ' + response.status + ')');
            speakWithBrowserTts(text, component);
        } else {
            console.warn('TTS: Falling back to browser TTS - server error (status ' + response.status + ')');
            speakWithBrowserTts(text, component);
        }
    } catch (error) {
        console.warn('TTS: Falling back to browser TTS - fetch error:', error.message);
        speakWithBrowserTts(text, component);
    }
};

/**
 * Fallback: Speak using browser's speech synthesis.
 */
function speakWithBrowserTts(text, component) {
    if (!speechSynthesis) {
        console.warn('Speech synthesis not supported');
        if (component) {
            component.$server.onSpeakingStopped();
        }
        return;
    }

    // Cancel any ongoing speech
    speechSynthesis.cancel();

    const utterance = new SpeechSynthesisUtterance(text);
    utterance.lang = 'en-US';
    utterance.rate = 1.0;
    utterance.pitch = 1.0;

    // Try to use a good voice
    const voices = speechSynthesis.getVoices();
    const preferredVoices = voices.filter(v =>
        v.lang.startsWith('en') && (v.name.includes('Google') || v.name.includes('Samantha') || v.name.includes('Daniel'))
    );
    if (preferredVoices.length > 0) {
        utterance.voice = preferredVoices[0];
    } else {
        const englishVoice = voices.find(v => v.lang.startsWith('en'));
        if (englishVoice) {
            utterance.voice = englishVoice;
        }
    }

    utterance.onstart = () => {
        if (component) {
            component.$server.onSpeakingStarted();
        }
    };

    utterance.onend = () => {
        if (component) {
            component.$server.onSpeakingStopped();
        }
    };

    utterance.onerror = (event) => {
        console.error('Speech synthesis error:', event.error);
        if (component) {
            component.$server.onSpeakingStopped();
        }
    };

    speechSynthesis.speak(utterance);
}

/**
 * Stop current audio playback.
 */
function stopCurrentAudio() {
    if (currentAudio) {
        currentAudio.pause();
        currentAudio.currentTime = 0;
        currentAudio = null;
    }
}

/**
 * Stop speaking.
 */
window.stopSpeaking = function() {
    // Stop server TTS audio
    stopCurrentAudio();
    // Stop browser TTS
    if (speechSynthesis) {
        speechSynthesis.cancel();
    }
};

/**
 * Check if speech recognition is supported.
 */
window.isSpeechRecognitionSupported = function() {
    return !!SpeechRecognition;
};

/**
 * Check if speech synthesis is supported.
 */
window.isSpeechSynthesisSupported = function() {
    return !!speechSynthesis;
};

// Preload voices (some browsers need this)
if (speechSynthesis) {
    speechSynthesis.getVoices();
    speechSynthesis.onvoiceschanged = () => {
        speechSynthesis.getVoices();
    };
}
