package org.github.sipuada.plugins;

import java.util.HashMap;
import java.util.Map;

import org.github.sipuada.Constants.RequestMethod;
import org.github.sipuada.SipUserAgent;
import org.github.sipuada.exceptions.SipuadaException;
import org.github.sipuada.plugins.SipuadaPlugin.SipuadaPluginIntent;
import org.mockito.internal.matchers.Equals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.javax.sdp.SessionDescription;

public abstract class SipuadaPlugin {

	private final Map<SipuadaPluginIntent, RequestMethod[]> explicitPluginIntents = new HashMap<>();

	public enum SipuadaPluginIntent {

		HANDLE_SESSION(new RequestMethod[] { RequestMethod.INVITE }), HANDLE_EARLY_MEDIA(
				new RequestMethod[] { RequestMethod.INVITE }), HANDLE_GENERIC_PAYLOAD(new RequestMethod[] {});

		private RequestMethod[] implicitIntentMethods;

		public RequestMethod[] getImplicitIntentMethods() {
			return implicitIntentMethods;
		}

		private SipuadaPluginIntent(RequestMethod[] methods) {
			this.implicitIntentMethods = methods;
		}
	}

	public void declareHandleSessionIntent() {
		logger.info("declareHandleSessionIntent");
		declareSipuadaPluginIntent(SipuadaPluginIntent.HANDLE_SESSION);
	}

	public void declareHandleEarlyMediaIntent() {
		logger.info("declareHandleEarlyMediaIntent");
		declareSipuadaPluginIntent(SipuadaPluginIntent.HANDLE_EARLY_MEDIA);
	}

	public void declareHandleGenericPayload(RequestMethod[] desiredMethods) {
		logger.info("declareHandleGenericPayload");
		declareSipuadaPluginIntent(SipuadaPluginIntent.HANDLE_GENERIC_PAYLOAD, desiredMethods);
	}

	private void declareSipuadaPluginIntent(SipuadaPluginIntent intent) {
		logger.info("declareSipuadaPluginIntent");
		declareSipuadaPluginIntent(intent, new RequestMethod[] {});
	}

	private final Logger logger = LoggerFactory.getLogger(SipuadaPlugin.class);

	private void declareSipuadaPluginIntent(SipuadaPluginIntent intent, RequestMethod[] desiredMethods) {
		logger.info("declareSipuadaPluginIntent");

		if (intent.implicitIntentMethods.length != 0) {
			explicitPluginIntents.put(intent, intent.implicitIntentMethods);
		} else {
			if (desiredMethods.length == 0) {
				throw new SipuadaException("Plugin cannot declare intent of handling generic payloads "
						+ "without specifying explicit target methods.", null);
			}
			logger.info("explicitPluginIntents.put");
			explicitPluginIntents.put(intent, desiredMethods);
		}
	}

	public final Map<SipuadaPluginIntent, RequestMethod[]> declaredPluginIntents() {
		logger.info("declaredPluginIntents");
		return explicitPluginIntents;
	}

	/**
	 * Generates offer to go along a session-creating request of given method.
	 * 
	 * @return a SessionDescription representing an offer or null if the plug-in
	 *         wishes to propose no offer to a request of given method.
	 */
	public abstract SessionDescription generateOffer(String callId, RequestMethod method, String localAddress);

	/**
	 * Generates an SDP offer to go along the 183 (Session Progress) response.
	 * 
	 * @return a SessionDescription if the plug-in wishes early media.
	 */
	public abstract SessionDescription generateOfferForEarlyMedia(String callId, String localAddress);

	/**
	 * Feeds the accepted answer to a given offer back to the plug-in that
	 * generated that offer. The plug-in should know which offer this answer
	 * corresponds to by comparing the given callId with the one that was passed
	 * to generateOffer(). This method is important because the plug-in may need
	 * to use both the original offer and the accepted answer to perform the
	 * session setup stage. If this instance receives a generateOffer(), it MUST
	 * expect an upcoming receiveAcceptedAnswer() in the future (and then, a
	 * performSessionSetup()). If this instance receives a generateAnswer(), it
	 * MUST NOT expect an upcoming receiveAcceptedAnswer() because obviously
	 * this was the plug-in that accepted someone else's offer, and thus it must
	 * only expect a performSessionSetup() later.
	 */
	public abstract void receiveAnswerToAcceptedOffer(String callId, SessionDescription answer);

	/**
	 * Generates an answer to an offer to go along a response to a
	 * session-creating request of given method.
	 * 
	 * @return a SessionDescription representing the answer to an offer or null
	 *         if the plug-in could not elaborate a valid answer to it.
	 */
	public abstract SessionDescription generateAnswer(String callId, RequestMethod method, SessionDescription offer,
			String localAddress);

	/**
	 * Perform session setup since offer/answer sent alongside a call invitation
	 * request/response with given callId was established successfully (or is
	 * expected to). The UserAgent is passed along so that the plug-in can ask
	 * it to perform session modification requests in the future.
	 * 
	 * @return true if the plug-in could setup the session, false otherwise.
	 */
	public abstract boolean performSessionSetup(String callId, SipUserAgent userAgent);

	/**
	 * Perform session termination since offer/answer sent alongside a call
	 * invitation request/response with given callId could not establish a call
	 * or established a call that was recently finished.
	 * 
	 * @return true if the plug-in could terminate the session, false otherwise.
	 */
	public abstract boolean performSessionTermination(String callId);

}
