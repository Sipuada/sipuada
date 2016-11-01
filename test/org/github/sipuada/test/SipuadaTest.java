package org.github.sipuada.test;

import java.util.List;

import org.github.sipuada.Sipuada;
import org.github.sipuada.SipuadaApi.BasicRequestCallback;
import org.github.sipuada.SipuadaApi.CallInvitationCallback;
import org.github.sipuada.SipuadaApi.SipuadaListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SipuadaTest {

	private static final Logger logger = LoggerFactory.getLogger(SipuadaTest.class);

	public static void main(String[] args) {
		SipuadaListener sipuadaListener = new SipuadaListener() {

			@Override
			public boolean onCallInvitationArrived(String localUser, String localUserDomain, String callId,
					String remoteUsername, String remoteHost, boolean shouldExpectEarlyMedia) {
				logger.debug("onCallInvitationArrived: [localUser={{}}, localUserDomain={{}}, callId={{}};" +
					" remoteUsername={{}}; remoteHost={{}}; shouldExpectEarlyMedia={{}}].", localUser,
					localUserDomain, callId, remoteUsername, remoteHost, shouldExpectEarlyMedia);
				return false;
			}

			@Override
			public void onCallInvitationCanceled(String localUser, String localUserDomain, String reason, String callId) {
				logger.debug("onCallInvitationCanceled: [localUser={{}}, localUserDomain={{}}, reason={{}}; callId={{}}].", localUser, localUserDomain, reason, callId);
			}

			@Override
			public void onCallInvitationFailed(String localUser, String localUserDomain, String reason, String callId) {
				logger.debug("onCallInvitationFailed: [localUser={{}}, localUserDomain={{}}, reason={{}}; callId={{}}].", localUser, localUserDomain, reason, callId);
			}

			@Override
			public void onCallEstablished(String localUser, String localUserDomain, String callId) {
				logger.debug("onCallEstablished: [localUser={{}}, localUserDomain={{}}, callId={{}}].", localUser, localUserDomain, callId);
			}

			@Override
			public void onCallFinished(String localUser, String localUserDomain, String callId) {
				logger.debug("onCallFinished: [localUser={{}}, localUserDomain={{}}, callId={{}}].", localUser, localUserDomain, callId);
			}

			@Override
			public void onCallFailure(String localUser, String localUserDomain, String reason, String callId) {
				logger.debug("onCallFailure: [localUser={{}}, localUserDomain={{}}, reason={{}}; callId={{}}].", localUser, localUserDomain, reason, callId);
			}

			@Override
			public void onMessageReceived(String localUser, String localUserDomain,
					String callId, String remoteUser, String remoteDomain, String content,
					String contentType, String... additionalHeaders) {
				logger.debug("onMessageReceived: [localUser={{}}, localUserDomain={{}}, callId={{}}; remoteUser={{}}; remoteDomain={{}}; content={{}};"
					+ " contentType={{}}; additionalHeaders={{}};].", localUser, localUserDomain, callId, remoteUser, remoteDomain,
					content, contentType, additionalHeaders);
			}

		};
		Sipuada sipuada = new Sipuada(sipuadaListener,
				"xibaca", "192.168.130.207:5060", "xibaca",
				"192.168.130.207:55501/TCP",
				"192.168.130.207:55502/TCP"
		);
		BasicRequestCallback registrationCallback =
				new BasicRequestCallback() {

			@Override
			public void onRequestSuccess(String localUser, String localUserDomain, Object... response) {
				for (Object object : response) {
					List<String> registeredContacts = (List<String>) object;
					logger.debug("onRegistrationSuccess: [localUser={{}}, localUserDomain={{}}, registeredContacts={{}}].", localUser, localUserDomain, registeredContacts);
				}
			}

			@Override
			public void onRequestFailed(String localUser, String localUserDomain, String reason) {
				logger.debug("onRegistrationFailed: [localUser={{}}, localUserDomain={{}}, reason={{}}].", localUser, localUserDomain, reason);
			}

		};
		sipuada.registerAddresses(registrationCallback);

		sipuada.unregisterAddresses(registrationCallback,
				"192.168.130.207:55502/TCP");

		sipuada.overwriteUserAgents(registrationCallback,
				"192.168.130.207:55503/TCP");

		CallInvitationCallback callInvitationCallback = new CallInvitationCallback() {

			@Override
			public void onWaitingForCallInvitationAnswer(String localUser, String localUserDomain, String callId) {
				logger.debug("onWaitingForCallInvitationAnswer: [localUser={{}}, localUserDomain={{}}, callId={{}}].", localUser, localUserDomain, callId);
			}

			@Override
			public void onCallInvitationRinging(String localUser, String localUserDomain, String callId, boolean shouldExpectEarlyMedia) {
				logger.debug("onCallInvitationRinging: [localUser={{}}, localUserDomain={{}}, callId={{}}, shouldExpectEarlyMeadia={{}}].",
					localUser, localUserDomain, callId, shouldExpectEarlyMedia);
			}

			@Override
			public void onCallInvitationDeclined(String localUser, String localUserDomain, String reason) {
				logger.debug("onCallInvitationDeclined: [localUser={{}}, localUserDomain={{}}, reason={{}}].", localUser, localUserDomain, reason);
			}

		};

		sipuada.inviteToCall("larson", "192.168.130.207:5060", callInvitationCallback);

		sipuada.includeUserAgents(registrationCallback,
				"192.168.130.207:55504/TCP",
				"192.168.130.207:55505/TCP");

		sipuada.overwriteUserAgents(registrationCallback);

		sipuada.registerAddresses(registrationCallback);

		sipuada.overwriteUserAgents(registrationCallback,
				"192.168.130.207:55506/UDP",
				"192.168.130.207:55507/UDP",
				"192.168.130.207:55508/UDP");

		sipuada.clearAddresses(registrationCallback);

		sipuada.overwriteUserAgents(registrationCallback,
				"192.168.130.207:55507/UDP");

		try {
			Thread.sleep(30000);
		} catch (InterruptedException ignore) {}
		sipuada.inviteToCall("larson", "192.168.130.207:5060", callInvitationCallback);

		sipuada.unregisterAddresses(registrationCallback);
	}

}
