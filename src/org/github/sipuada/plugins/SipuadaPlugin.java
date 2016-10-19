package org.github.sipuada.plugins;

import org.github.sipuada.Constants.RequestMethod;
import org.github.sipuada.SipUserAgent;
import org.github.sipuada.plugins.SipuadaPlugin.SessionType;

import android.javax.sdp.SessionDescription;

public interface SipuadaPlugin {

	public enum SessionType {
		REGULAR("session"),
		EARLY("early-session");

		private String disposition;

		SessionType(String disposition) {
			this.disposition = disposition;
		}

		public String getDisposition() {
			return disposition;
		}

	}

	/**
	 * Generates offer to go along
	 * a session-creating request of given method.
	 * @return a SessionDescription representing an offer or null if the plug-in
	 * wishes to propose no offer to a request of given method.
	 */
	SessionDescription generateOffer(String callId, SessionType type,
			RequestMethod method, String localAddress);

	/**
	 * Feeds the accepted answer to a given offer back to the plug-in that generated
	 * that offer. The plug-in should know which offer this answer corresponds to
	 * by comparing the given callId with the one that was passed to generateOffer().
	 * This method is important because the plug-in may need to use both
	 * the original offer and the accepted answer to perform the session setup stage.
	 * If this instance receives a generateOffer(), it MUST expect an upcoming
	 * receiveAcceptedAnswer() in the future (and then, a performSessionSetup()).
	 * If this instance receives a generateAnswer(), it MUST NOT expect an upcoming
	 * receiveAcceptedAnswer() because obviously this was the plug-in that accepted
	 * someone else's offer, and thus it must only expect a performSessionSetup() later.
	 */
	void receiveAnswerToAcceptedOffer(String callId, SessionType type, SessionDescription answer);

	/**
	 * Generates an answer to an offer to go along a response
	 * to a session-creating request of given method.
	 * @return a SessionDescription representing the answer to an offer
	 * or null if the plug-in could not elaborate a valid answer to it.
	 */
	SessionDescription generateAnswer(String callId, SessionType type,
			RequestMethod method, SessionDescription offer, String localAddress);

	/**
	 * Perform session setup since offer/answer sent alongside
	 * a call invitation request/response with given callId
	 * was established successfully (or is expected to).
	 * The UserAgent is passed along so that the plug-in can ask
	 * it to perform session modification requests in the future.
	 * @return true if the plug-in could setup the session, false otherwise.
	 */
	boolean performSessionSetup(String callId, SessionType type, SipUserAgent userAgent);

	/**
	 * Perform session termination since offer/answer sent alongside
	 * a call invitation request/response with given callId could not
	 * establish a call or established a call that was recently finished.
	 * @return true if the plug-in could terminate the session, false otherwise.
	 */
	boolean performSessionTermination(String callId, SessionType type);

}
