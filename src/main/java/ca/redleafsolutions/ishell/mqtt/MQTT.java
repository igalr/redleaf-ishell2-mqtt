package ca.redleafsolutions.ishell.mqtt;

import java.util.UUID;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttSecurityException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import ca.redleafsolutions.BaseList;
import ca.redleafsolutions.Trace;
import ca.redleafsolutions.base.events.EventDispatcher;
import ca.redleafsolutions.base.events.EventHandler;
import ca.redleafsolutions.ishell2.annotations.IShellInvisible;
import ca.redleafsolutions.ishell2.annotations.MethodDescription;
import ca.redleafsolutions.ishell2.annotations.ParameterDescriptions;
import ca.redleafsolutions.ishell2.annotations.ParameterNames;
import ca.redleafsolutions.json.JSONItem;
import ca.redleafsolutions.json.JSONValidationException;
import ca.redleafsolutions.json.JSONWritable;

public class MQTT extends EventDispatcher<QEvent> implements JSONWritable, MqttCallback {
	private MqttClient client;
	private String endpoint;
	private int qos;
	private String name;
	private String username;
	private String password;
	private boolean deliveryEvent;
	private MqttConnectOptions opt;

	public MQTT (JSONItem json) throws JSONValidationException, MqttException {
		this.endpoint = json.getString ("endpoint");
		try {
			qos = json.getInt ("qos");
		} catch (JSONValidationException e) {
			qos = 2;
		}

		try {
			this.name = json.getString ("name");
		} catch (JSONValidationException e) {
			this.name = UUID.randomUUID ().toString ();
		}

		try {
			this.username = json.getString ("username");
			this.password = json.getString ("password");
		} catch (JSONValidationException e) {
			this.username = null;
			this.password = null;
		}

		try {
			this.deliveryEvent = json.getBoolean ("notify-delivery");
		} catch (JSONValidationException e) {
			this.deliveryEvent = false;
		}

		connect ();
	}

	public MQTT (String endpoint) {
		this.endpoint = endpoint;
	}

	public MQTT name (String name) {
		this.name = name;
		return this;
	}

	public MQTT username (String username) {
		this.username = username;
		return this;
	}

	public MQTT password (String password) {
		this.password = password;
		return this;
	}

	public MQTT qos (int qos) {
		this.qos = qos;
		return this;
	}

	public MQTT deliveryEvent (boolean deliveryEvent) {
		this.deliveryEvent = deliveryEvent;
		return this;
	}

	protected String getName () {
		return name;
	}

	public void connect () throws MqttSecurityException, MqttException {
		if (this.client == null) {
			client = new MqttClient (endpoint, UUID.randomUUID ().toString (), new MemoryPersistence ());
			client.setCallback (this);
		}

		if (this.client.isConnected ())
			return;

		this.opt = new MqttConnectOptions ();
		opt.setCleanSession (false);
		opt.setAutomaticReconnect (true);
		if (this.username != null && this.password != null) {
			opt.setUserName (this.username);
			opt.setPassword (this.password.toCharArray ());
		}
		client.connect (opt);
	}

	public void disconnect () throws MqttException {
		client.disconnect ();
		client.close ();
	}

	private Object isConnected () {
		if (client == null)
			return false;
		return client.isConnected ();
	}

	@MethodDescription ("Subscribe to a topic")
	@ParameterNames ("topic")
	@ParameterDescriptions ("Topic channel")
	public void sub (String topic) throws MqttException {
		client.subscribe (topic);
	}

	@MethodDescription ("Unsubscribe from a topic")
	@ParameterNames ("topic")
	@ParameterDescriptions ("Topic channel")
	public void unsub (String topic) throws MqttException {
		client.unsubscribe (topic);
	}

	@MethodDescription ("Publish a message to a topic channel")
	@ParameterNames ({ "topic", "message" })
	@ParameterDescriptions ({ "Topic channel", "Message to send to channel" })
	public void pub (String topic, String message) throws MqttException {
		MqttMessage mqttmessage = new MqttMessage (message.getBytes ());
		mqttmessage.setQos (qos);
		client.publish (topic, mqttmessage);
	}

	@IShellInvisible
	public JSONItem toJSON () throws JSONValidationException {
		JSONItem json = JSONItem.newObject ();
		json.put ("id", client.getClientId ());
		json.put ("current-server-uri", client.getCurrentServerURI ());
		json.put ("pending-delivery-tokens", client.getPendingDeliveryTokens ());
		json.put ("server-uri", client.getServerURI ());
		json.put ("time-to-wait", client.getTimeToWait ());

		json.put ("username", opt.getUserName ());
		json.put ("connection-timeout", opt.getConnectionTimeout ());
		json.put ("keepalive-interval", opt.getKeepAliveInterval ());
		json.put ("max-inflight", opt.getMaxInflight ());
		json.put ("max-reconnect-delay", opt.getMaxReconnectDelay ());
		json.put ("mqtt-version", opt.getMqttVersion ());
		if (opt.getServerURIs () != null)
			json.put ("server-uris", new BaseList<String> (opt.getServerURIs ()));

		return json;
	}

	public void connectionLost (Throwable e) {
		dispatchEvent (new QEvent.ConnectionLost (this, e));
	}

	public void deliveryComplete (IMqttDeliveryToken token) {
		if (!deliveryEvent)
			return;
		dispatchEvent (new QEvent.MessageSent (this, token.getMessageId ()));
	}

	public void messageArrived (String topic, MqttMessage message) throws Exception {
//		Trace.info (name + "> " + topic + ": " + message);
		dispatchEvent (new QEvent.MessageReceived (this, topic, message.getPayload ()));
	}

	public static void main (String[] args) throws MqttSecurityException, MqttException, JSONValidationException, InterruptedException {
		MQTT mqtt1 = new MQTT ("tcp://localhost:1883");
		mqtt1.name ("Q1").username ("some-username").password ("some-password").qos (2).deliveryEvent (false).connect ();

		JSONItem json = JSONItem.parse ("{"
				+ "\"endpoint\": \"tcp://localhost:1883\","
				+ "\"name\": \"Q2\","
				+ "\"username\": \"some-username\","
				+ "\"password\": \"some-password\","
				+ "\"notify-delivery\": false"
				+ "}");

		MQTT mqtt2 = new MQTT (json);

		Trace.on ();
		Trace.info (mqtt1.isConnected (), mqtt2.isConnected ());
		mqtt1.sub ("some-topic");
		mqtt1.addEventHandler (new EventHandler<QEvent> () {
			public void handleEvent (QEvent event) {
				Trace.info ("Message received by " + event.getClient ().getName ());
				System.exit (0);
			}
		});

		Thread.sleep (2000);
		mqtt2.pub ("some-topic", "This is a message from " + mqtt2.getName ());
	}
}
