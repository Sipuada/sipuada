package org.github.sipuadaui;

import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTextField;

import net.miginfocom.swing.MigLayout;

import javax.swing.JButton;
import javax.swing.JPasswordField;
import javax.swing.JTextArea;
import org.github.sipuada.Sipuada;
import org.github.sipuada.SipuadaApi.CallInvitationCallback;
import org.github.sipuada.SipuadaApi.OptionsQueryingCallback;
import org.github.sipuada.SipuadaApi.RegistrationCallback;
import org.github.sipuada.SipuadaApi.SipuadaListener;

import android.gov.nist.gnjvx.sip.Utils;
import android.javax.sdp.SessionDescription;

public class SIPClientMain implements SipuadaListener {

	private JFrame frmSipuada;
	private JTextField registrarDomainTextField;
	private JTextField registrarUserNameTextField;
	private JTextField callerDomainTextField;
	private JTextField callerUserTextField;
	private JTextField passwordField;
	private JTextArea textArea;
	private Sipuada sipuada;
	private String currentCallID;
	private String currentInviteCallID;
	private JButton btAcceptCall;
	private JButton btRejectCall;
	private boolean isBusy = false;
	private JButton btnCancel;
	private JButton btnEndCall;
	private JButton btCall;
	private JButton btOptions;

	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					SIPClientMain window = new SIPClientMain();
					window.frmSipuada.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	/**
	 * Create the application.
	 */
	public SIPClientMain() {
		initialize();
		setDefautValues();
	}

	private void setDefautValues() {
		registrarDomainTextField.setText("192.168.130.207:5060");
		registrarUserNameTextField.setText("renan");
		passwordField.setText("renan");
		callerDomainTextField.setText("192.168.130.207:5060");
	}

	private void setUPCallButton(final JButton callButton) {

		callButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (sipuada == null) {
					textArea.setText(textArea.getText()
							+ System.getProperty("line.separator") + " - "
							+ " Required to register!");
					btnCancel.setEnabled(false);
					callButton.setEnabled(true);
				}
				callButton.setEnabled(false);
				sipuada.inviteToCall(callerUserTextField.getText(),
						callerDomainTextField.getText(),
						new CallInvitationCallback() {
							@Override
							public void onWaitingForCallInvitationAnswer(
									String callId) {
								textArea.setText(textArea.getText()
										+ System.getProperty("line.separator")
										+ " - "
										+ " Waiting For Call InvitationAnswer ...");
								currentCallID = callId;
								btnCancel.setEnabled(true);
							}

							@Override
							public void onCallInvitationRinging(String callId) {
								textArea.setText(textArea.getText()
										+ System.getProperty("line.separator")
										+ " - " + " Ringing ...");
								btnCancel.setEnabled(false);
								currentCallID = callId;
								btnCancel.setEnabled(true);
							}

							@Override
							public void onCallInvitationDeclined(String reason) {
								textArea.setText(textArea.getText()
										+ System.getProperty("line.separator")
										+ " - " + "Invitation Declined.");
								btnCancel.setEnabled(false);
								callButton.setEnabled(true);
							}
						});
			}
		});
	}
	
	private void setUPOptionsButton(final JButton optionsButton) {
		optionsButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (sipuada == null) {
					textArea.setText(textArea.getText()
							+ System.getProperty("line.separator") + " - "
							+ " Required to register!");
					btnCancel.setEnabled(false);
					optionsButton.setEnabled(true);
				}
				optionsButton.setEnabled(false);
				sipuada.queryOptions(callerUserTextField.getText(),
						callerDomainTextField.getText(),
						new OptionsQueryingCallback() {
							
							@Override
							public void onOptionsQueryingSuccess(String callId, SessionDescription sdpContent) {
								textArea.setText(textArea.getText()
										+ System.getProperty("line.separator")
										+ " - " + " Querying Success ...");
								currentCallID = callId;
								optionsButton.setEnabled(true);
							}
														
							@Override
							public void onOptionsQueryingFailed(String reason) {
								textArea.setText(textArea.getText()
										+ System.getProperty("line.separator")
										+ " - " + " Querying Failed ...");
								optionsButton.setEnabled(true);
							}

							@Override
							public void onOptionsQueryingArrived(String callId) {
								textArea.setText(textArea.getText()
										+ System.getProperty("line.separator")
										+ " - " + " Ringing ...");
								currentCallID = callId;
								optionsButton.setEnabled(true);
							}
						});
			}
		});
	}
	

	private void setEndCallButton(JButton endCall) {
		endCall.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				sipuada.finishCall(currentCallID);
			}
		});
	}

	private void setUpRegisterButton(JButton registerButton) {

		registerButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (sipuada == null) {
					sipuada = new Sipuada(SIPClientMain.this,
							registrarUserNameTextField.getText(),
							registrarDomainTextField.getText(), passwordField
									.getText(), Util.getIPAddress(true) + ":55000/TCP");
				}
				sipuada.registerCaller(new RegistrationCallback() {
					@Override
					public void onRegistrationSuccess(
							List<String> registeredContacts) {
						textArea.setText(textArea.getText()
								+ System.getProperty("line.separator") + " - "
								+ " successfully registered");
					}

					@Override
					public void onRegistrationRenewed() {
						textArea.setText(textArea.getText()
								+ System.getProperty("line.separator") + " - "
								+ " Registration Renewed");
					}

					@Override
					public void onRegistrationFailed(String reason) {
						textArea.setText(textArea.getText()
								+ System.getProperty("line.separator") + " - "
								+ " failure to register: " + reason);
					}
				});
			}
		});

	}

	private void setUpAcceptCallButton(JButton btAcceptCall) {
		btAcceptCall.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				sipuada.acceptCallInvitation(currentInviteCallID);
			}
		});
	}

	private void setUpRejectCallButton(JButton btRejectCall) {
		btRejectCall.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				sipuada.declineCallInvitation(currentInviteCallID);
			}
		});
	}

	private void setUpCancelButton(JButton btCancel) {
		btCancel.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				sipuada.cancelCallInvitation(currentCallID);
				btnCancel.setEnabled(false);
			}
		});
	}

	/**
	 * Initialize the contents of the frame.
	 */
	private void initialize() {
		frmSipuada = new JFrame();
		frmSipuada.setTitle("SIP User Agent - Sipuada");
		frmSipuada.setBounds(100, 100, 800, 600);
		frmSipuada.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		JLabel lblNewLabel = new JLabel("Domain");

		registrarDomainTextField = new JTextField();
		registrarDomainTextField.setColumns(10);

		JLabel label = new JLabel("User");

		registrarUserNameTextField = new JTextField();
		registrarUserNameTextField.setColumns(10);

		JLabel label_1 = new JLabel("Password");
		frmSipuada.getContentPane().setLayout(
				new MigLayout("", "[207px,grow][142px,grow][142px,grow][][]",
						"[15px][19px][][][][grow][][]"));
		frmSipuada.getContentPane().add(registrarUserNameTextField,
				"cell 1 1,growx,aligny top");
		frmSipuada.getContentPane().add(label_1,
				"cell 2 0,alignx left,aligny top");
		frmSipuada.getContentPane().add(registrarDomainTextField,
				"cell 0 1,growx,aligny top");
		frmSipuada.getContentPane().add(lblNewLabel,
				"cell 0 0,alignx left,aligny top");
		frmSipuada.getContentPane().add(label, "cell 1 0");

		passwordField = new JTextField();
		frmSipuada.getContentPane().add(passwordField,
				"cell 2 1,growx,aligny top");

		JButton btnRegister = new JButton("Register");
		setUpRegisterButton(btnRegister);
		frmSipuada.getContentPane().add(btnRegister, "cell 4 1");

		JLabel label_2 = new JLabel("Domain");
		frmSipuada.getContentPane().add(label_2, "cell 0 2");

		JLabel lblNewLabel_1 = new JLabel("User");
		frmSipuada.getContentPane().add(lblNewLabel_1, "cell 1 2");

		callerDomainTextField = new JTextField();
		frmSipuada.getContentPane()
				.add(callerDomainTextField, "cell 0 3,growx");
		callerDomainTextField.setColumns(10);

		callerUserTextField = new JTextField();
		frmSipuada.getContentPane().add(callerUserTextField, "cell 1 3,growx");
		callerUserTextField.setColumns(10);

		btCall = new JButton("Call");
		setUPCallButton(btCall);
		frmSipuada.getContentPane().add(btCall, "cell 4 3");
		
		btOptions = new JButton("Options");
		setUPOptionsButton(btOptions);
		frmSipuada.getContentPane().add(btOptions, "cell 4 3");

		JLabel lblLog = new JLabel("Log");
		frmSipuada.getContentPane().add(lblLog, "cell 0 4");

		textArea = new JTextArea();
		frmSipuada.getContentPane().add(textArea, "cell 0 5 5 1,grow");

		btAcceptCall = new JButton("Accept Call");
		btAcceptCall.setEnabled(false);
		setUpAcceptCallButton(btAcceptCall);
		frmSipuada.getContentPane().add(btAcceptCall, "cell 0 7");

		btRejectCall = new JButton("Reject Call");
		btRejectCall.setEnabled(false);
		setUpRejectCallButton(btRejectCall);
		frmSipuada.getContentPane().add(btRejectCall, "cell 1 7");

		btnEndCall = new JButton("End Call");
		frmSipuada.getContentPane().add(btnEndCall, "cell 2 7");
		btnEndCall.setEnabled(false);
		setEndCallButton(btnEndCall);

		btnCancel = new JButton("Cancel");
		setUpCancelButton(btnCancel);
		btnCancel.setEnabled(false);
		frmSipuada.getContentPane().add(btnCancel, "cell 4 7");
	}

	@Override
	public boolean onCallInvitationArrived(String callId) {
		textArea.setText(textArea.getText()
				+ System.getProperty("line.separator") + " - "
				+ " Call Invitation Arrived.");
		btAcceptCall.setEnabled(true);
		btRejectCall.setEnabled(true);
		this.currentInviteCallID = callId;
		return isBusy;
	}

	@Override
	public void onCallInvitationCanceled(String reason, String callId) {
		textArea.setText(textArea.getText()
				+ System.getProperty("line.separator") + " - "
				+ " Call Invitation Canceled.");
		btAcceptCall.setEnabled(false);
		btRejectCall.setEnabled(false);
		btnCancel.setEnabled(false);
		btCall.setEnabled(true);
	}

	@Override
	public void onCallInvitationFailed(String reason, String callId) {
		textArea.setText(textArea.getText()
				+ System.getProperty("line.separator") + " - "
				+ " Call Invitation Failed: " + reason);
		btAcceptCall.setEnabled(false);
		btRejectCall.setEnabled(false);
		btnCancel.setEnabled(false);
		btCall.setEnabled(true);

	}

	@Override
	public void onCallEstablished(String callId) {
		textArea.setText(textArea.getText()
				+ System.getProperty("line.separator") + " - "
				+ " Call Established.");
		btAcceptCall.setEnabled(false);
		btRejectCall.setEnabled(false);
		btnCancel.setEnabled(false);
		btnEndCall.setEnabled(true);
		this.currentCallID = callId;
		isBusy = true;
	
		
	}

	@Override
	public void onCallFinished(String callId) {
		textArea.setText(textArea.getText()
				+ System.getProperty("line.separator") + " - "
				+ " Call Finished.");
		btAcceptCall.setEnabled(false);
		btRejectCall.setEnabled(false);
		btnEndCall.setEnabled(false);
		btCall.setEnabled(true);
		isBusy = false;

	}
}
