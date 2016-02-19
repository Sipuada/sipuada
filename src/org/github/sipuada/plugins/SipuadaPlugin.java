package org.github.sipuada.plugins;

import org.github.sipuada.Constants.RequestMethod;
import org.github.sipuada.UserAgent;

import android.javax.sdp.SessionDescription;

public interface SipuadaPlugin {

	/**
	 * Generates offer to go along
	 * a session-creating request of given method.
	 * @return a SessionDescription representing an offer or null if the plug-in
	 * wishes to propose no offer to a request of given method.
	 */
	SessionDescription generateOffer(String callId, RequestMethod method);

	/**
	 * Generates an answer to an offer to go along a response
	 * to a session-creating request of given method.
	 * @return a SessionDescription representing the answer to an offer
	 * or null if the plug-in could not elaborate a valid answer to it.
	 */
	SessionDescription generateAnswer(String callId, RequestMethod method, SessionDescription offer);

	/**
	 * Perform session setup since offer/answer sent alongside
	 * a call invitation request/response with given callId
	 * was established successfully (or is expected to).
	 * The UserAgent is passed along so that the plug-in can ask
	 * it to perform session modification requests in the future.
	 * @return true if the plug-in could setup the session, false otherwise.
	 */
	boolean performSessionSetup(String callId, UserAgent userAgent);

	/**
	 * Perform session termination since offer/answer sent alongside
	 * a call invitation request/response with given callId could not
	 * establish a call or established a call that was recently finished.
	 * @return true if the plug-in could terminate the session, false otherwise.
	 */
	boolean performSessionTermination(String callId);

}
