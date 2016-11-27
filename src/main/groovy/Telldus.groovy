import groovy.util.logging.Slf4j
import com.amazonaws.services.lambda.runtime.Context
import static java.util.UUID.randomUUID
import com.github.scribejava.core.builder.ServiceBuilder
import com.github.scribejava.apis.LinkedInApi
import com.github.scribejava.core.model.OAuth1AccessToken
import com.github.scribejava.core.model.OAuth1RequestToken
import com.github.scribejava.core.model.OAuthRequest
import com.github.scribejava.core.model.Response
import com.github.scribejava.core.model.Verb
import com.github.scribejava.core.oauth.OAuth10aService
import groovy.json.*

@Slf4j
public class Telldus {

	public static void main(args) {
		def telldus = new Telldus()
		//telldus.handler([header:[messageId:'6d6d6e14-8aee-473e-8c24-0d31ff9c17a2', name:'TurnOffRequest', namespace:'Alexa.ConnectedHome.Control', payloadVersion:'2'], payload:[accessToken:'Atza|5A', appliance:[applianceId:1112915, additionalApplianceDetails:[extraDetail3:'but they should only be used for reference purposes.', extraDetail4:'This is not a suitable place to maintain current device state', extraDetail1:'optionalDetailForSkillAdapterToReferenceThisDevice, extraDetail2:There can be multiple entries']]]], null)
		//telldus.handler([header:[messageId:'6d6d6e14-8aee-473e-8c24-0d31ff9c17a2', name:'TurnOnRequest', namespace:'Alexa.ConnectedHome.Control', payloadVersion:'2'], payload:[accessToken:'Atza|5A', appliance:[applianceId:1112915, additionalApplianceDetails:[extraDetail3:'but they should only be used for reference purposes.', extraDetail4:'This is not a suitable place to maintain current device state', extraDetail1:'optionalDetailForSkillAdapterToReferenceThisDevice, extraDetail2:There can be multiple entries']]]], null)
		telldus.handler([header:[payloadVersion:2, namespace:'Alexa.ConnectedHome.Discovery', name:'DiscoverAppliancesRequest'], payload:[accessToken:'someaccesstoken']], null)

	}

	def handler(def event, Context context) {
		log.debug "Incoming event: ${event}"
		if (event.header.namespace == 'Alexa.ConnectedHome.Discovery') {
			log.debug "Alexa.ConnectedHome.Discovery - Sending response"
			return getDevicesRequest()
		} else if (event.header.namespace == 'Alexa.ConnectedHome.Control') {
			handleControlEvent event
		}
	}

	def handleControlEvent(event) {
		log.debug "Alexa.ConnectedHome.Control"
		def deviceId = event.payload.appliance.applianceId
		def messageId = event.header.messageId
		def headerName = event.header.name

		log.debug "Incoming request: ${headerName}"

		switch (headerName) {
			case "TurnOnRequest":
				return doRequest(deviceId, "turnOn", messageId, "TurnOnConfirmation", null, null)
				break
			case "TurnOffRequest":
				return doRequest(deviceId, "turnOff", messageId, "TurnOffConfirmation", null, null)
				break
			case "HealthCheckRequest":
				return doHealthCheckRequest()
				break
			case "SetPercentageRequest":
				def percentageState = event.payload.percentageState.value
				return doRequest(deviceId, "dim", messageId, "SetPercentageConfirmation", percentageState, null)
				break
			case "IncrementPercentageRequest":
				def deltaPercentage = event.payload.deltaPercentage.value
				return doRequest(deviceId, "dim", messageId, "IncrementPercentageConfirmation", deltaPercentage, 'up')
				break
			case "DecrementPercentageRequest":
				def deltaPercentage = event.payload.deltaPercentage.value
				return doRequest(deviceId, "dim", messageId, "DecrementPercentageConfirmation", deltaPercentage, 'down')
				break
			default:
				handleError
				break
		}
	}

	def messageId () {
		return randomUUID() as String
	}

	def doHealthCheckRequest() {
		def reply = [:]
		reply['header'] = ["namespace": 'Alexa.ConnectedHome.System', "name": HealthCheckResponse, "messageId": messageId(), "payloadVersion": '2']
		reply['payload'] = ["description": "The system is currently healthy","isHealthy": true]
		reply
	}

	def makeError(def name, def namespace, def payload) {
		def error = [:]
		error['header'] = ["namespace": namespace, "name": name, "messageId": messageId(), "payloadVersion": '2']
		error['payload'] = [:]
		error
	}

	def getCurrentState(def id) {
		def params = [:]
		params.put("id", id)
		OAuthRequest request = createAndSignRequest("device/info", params)
		Response response = request.send()
		JsonSlurper jsonSlurper = new JsonSlurper()
    def json = new JsonSlurper().parseText(response.getBody())
		json.statevalue
	}

	def doRequest(def id, def type, def messageId, def returnName, def level, def dimType) {
		def params = [:]
		def reply = [:]

		params.put("id", id)
		if (level) {
			def valueLevel = level.toInteger() * 2.55
			def currentValue= ''
			if(dimType == 'up') {
				currentValue = getCurrentState(id).toInteger()
				valueLevel = valueLevel + currentValue
			} else if (dimType == 'down') {
				currentValue = getCurrentState(id).toInteger()
				valueLevel = currentValue - valueLevel
			}
			log.debug "Dim value: " + valueLevel
			if (valueLevel > 255) {
				valueLevel = 255
			} else if (valueLevel < 0 ){
				valueLevel = 0
			}
			params.put("level", valueLevel)
		}
		OAuthRequest request = createAndSignRequest("device/" + type, params)

		log.debug "Sending request for id: " + id
		Response response = request.send()

		JsonSlurper jsonSlurper = new JsonSlurper()
        def json = new JsonSlurper().parseText(response.getBody())

		log.debug "Telldus reply: " + json

		if (json.status == 'success') {
			reply['header'] = ["namespace": 'Alexa.ConnectedHome.Control', "name": returnName, "messageId": messageId, "payloadVersion": '2']
		} else {
			return makeError('TargetOfflineError','Alexa.ConnectedHome.Control')
		}

		reply['payload'] = [:]

		log.debug "Control reply " + reply
		reply
	}

	def getDevicesRequest() {
	  def reply=[:]
		log.debug "getDevicesRequest"

		OAuthRequest request = createAndSignRequest("devices/list", ["supportedMethods":1023])
		Response response = request.send()
    def json = new JsonSlurper().parseText(response.getBody())

		reply['header'] = ["namespace": 'Alexa.ConnectedHome.Discovery', "name": 'DiscoverAppliancesResponse', "messageId": messageId(), "payloadVersion": '2']

		def getAllDevices = []
		json.device.each{ telldus->
			 def actions = ''

			 if (telldus."methods" == 51) {
				 actions = ["incrementPercentage", "decrementPercentage", "setPercentage", "turnOn", "turnOff"]
			 } else {
				 actions = ["turnOn", "turnOff"]
			 }

			 getAllDevices.add(["applianceId":telldus."id",
                    "manufacturerName":"Telldus",
                    "modelName":"Unknown",
                    "version":"1",
                    "friendlyName":telldus."name",
                    "friendlyDescription":"Telldus - " + telldus."name",
                    "isReachable":true,
                    "actions":actions,
                    "additionalApplianceDetails":[
						 "methods":telldus."methods"
						]
					])
		}
		reply['payload'] = ["discoveredAppliances":getAllDevices]

		log.debug "Device request reply: ${toPrettyJson reply}"
		reply
	}

	def telldusUrl(String extension) {
		"http://api.telldus.com/json/" + extension
	}

	def toPrettyJson(json) {
    JsonOutput.prettyPrint(JsonOutput.toJson(json))
  }

	def OAuthRequest createAndSignRequest(String url, Map<String, String> parameters) {
		log.debug "createAndSignRequest url: ${url} Parameters: ${parameters}"
		OAuth10aService oService = createAuthService()
		OAuthRequest request = createRequest(url, parameters, oService)
		OAuth1AccessToken accessToken = createAccessToken()
		oService.signRequest(accessToken, request)
		request
	}

	def OAuth1AccessToken createAccessToken() {
		Global global = new Global();
		String publicToken = global.getPublictoken()
		String secretToken = global.getSecrettoken()

		OAuth1AccessToken accessToken = new OAuth1AccessToken(publicToken, secretToken)
		accessToken
	}

	def OAuth10aService createAuthService() {
		Global global = new Global();
		String publicKey = global.getPublickey()
		String secretKey = global.getSecretkey()

		OAuth10aService oService = new ServiceBuilder().apiKey(publicKey).apiSecret(secretKey).build(TelldusApi.instance())
		oService
	}

	def OAuthRequest createRequest(String url, Map<String, String> parameters, service) {
		// Create, sign and send request.
		OAuthRequest request = new OAuthRequest(Verb.GET, telldusUrl(url), service)

		if (parameters) {
			parameters.each {key, value ->
				request.addQuerystringParameter(key, value.toString())
			}
		}
		request
	}

}
