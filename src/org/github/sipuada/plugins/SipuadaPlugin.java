package org.github.sipuada.plugins;

import java.util.Map;

import org.github.sipuada.SipUserAgent;

import android.javax.sdp.SessionDescription;

public interface SipuadaPlugin {

	public abstract void doStartPlugin();

	public abstract void doStopPlugin();

	public SessionDescription generateOffer(String callId, SessionType type,
		String localAddress);

	public void receiveAnswerToAcceptedOffer(String callId, SessionType type,
		SessionDescription answer);

	public SessionDescription generateAnswer(String callId, SessionType type,
		SessionDescription offer, String localAddress);

	public boolean performSessionSetup(String callId, SessionType type,
		SipUserAgent userAgent);

	public boolean performSessionTermination(String callId, SessionType type);

	public abstract boolean doSetupPreparedStreams(String callId, SessionType type,
		Map<String, Map<MediaCodecInstance, Session>> preparedStreams);

	public abstract boolean isSessionPrepared(String callId, SessionType type);

	public abstract boolean isSessionOngoing(String callId, SessionType type);

	public abstract boolean doTerminateStreams(String callId, SessionType type,
		Map<String, Map<MediaCodecInstance, Session>> ongoingStreams);

}
