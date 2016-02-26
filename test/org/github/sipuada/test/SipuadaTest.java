package org.github.sipuada.test;

import java.util.List;

import org.github.sipuada.Sipuada;
import org.github.sipuada.SipuadaApi.RegistrationCallback;
import org.github.sipuada.SipuadaApi.SipuadaListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SipuadaTest {

	private static final Logger logger = LoggerFactory.getLogger(SipuadaTest.class);

	public static void main(String[] args) {
		SipuadaListener sipuadaListener = new SipuadaListener() {

			@Override
			public boolean onCallInvitationArrived(String callId) {
				logger.debug("onCallInvitationArrived: [callId={{}}].", callId);
				return false;
			}

			@Override
			public void onCallInvitationCanceled(String reason, String callId) {
				logger.debug("onCallInvitationCanceled: [reason={{}}; callId={{}}].", reason, callId);
			}

			@Override
			public void onCallInvitationFailed(String reason, String callId) {
				logger.debug("onCallInvitationFailed: [reason={{}}; callId={{}}].", reason, callId);
			}

			@Override
			public void onCallEstablished(String callId) {
				logger.debug("onCallEstablished: [callId={{}}].", callId);
			}

			@Override
			public void onCallFinished(String callId) {
				logger.debug("onCallFinished: [callId={{}}].", callId);
			}

			@Override
			public void onCallFailure(String reason, String callId) {
				logger.debug("onCallFailure: [reason={{}}; callId={{}}].", reason, callId);
			}

		};
		Sipuada sipuada = new Sipuada(sipuadaListener, "xibaca", "192.168.25.217:5060", "xibaca",
				"192.168.25.217:55501/TCP",
				"192.168.25.217:55502/TCP"
		);
		RegistrationCallback registrationCallback = new RegistrationCallback() {

			@Override
			public void onRegistrationSuccess(List<String> registeredContacts) {
				logger.debug("onRegistrationSuccess: [registeredContacts={{}}].", registeredContacts);
			}

			@Override
			public void onRegistrationFailed(String reason) {
				logger.debug("onRegistrationFailed.");
			}

		};
//		try {
//			Thread.sleep(15000);
//		} catch (InterruptedException ignore) {}
		sipuada.registerAddresses(registrationCallback);
//		try {
//			Thread.sleep(15000);
//		} catch (InterruptedException ignore) {}
		sipuada.overwriteAddresses(registrationCallback, "192.168.25.217:55503/TCP");
//		try {
//			Thread.sleep(15000);
//		} catch (InterruptedException ignore) {}
		sipuada.includeAddresses(registrationCallback,
				"192.168.25.217:55504/TCP",
				"192.168.25.217:55505/TCP");
//		try {
//			Thread.sleep(15000);
//		} catch (InterruptedException ignore) {}
		sipuada.overwriteAddresses(registrationCallback);
//		try {
//			Thread.sleep(15000);
//		} catch (InterruptedException ignore) {}
		sipuada.registerAddresses(registrationCallback);
//		try {
//			Thread.sleep(15000);
//		} catch (InterruptedException ignore) {}
		sipuada.overwriteAddresses(registrationCallback,
				"192.168.25.217:55506/UDP",
				"192.168.25.217:55507/UDP",
				"192.168.25.217:55508/UDP");
//		try {
//			Thread.sleep(15000);
//		} catch (InterruptedException ignore) {}
		sipuada.overwriteAddresses(registrationCallback,
				"192.168.25.217:55507/UDP");
	}

}
