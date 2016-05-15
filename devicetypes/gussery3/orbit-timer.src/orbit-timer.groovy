/**
 *  Orbit Water Timer
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  20150421 - Initial Version based on what others have done. Thanks to all who have worked on this.
 */
/*
 * Capabilities
 * - Battery
 * - Configuration
 * - Refresh
 * - Switch
 * - Valve
*/

metadata {
	definition (name: "Orbit Timer", namespace: "gussery3", author: "Gene Ussery") {
		capability "Battery"
		capability "Configuration"
		capability "Refresh"
		capability "Switch"
		capability "Valve"

		fingerprint profileId: "0104", inClusters: "0000,0001,0003,0020,0006,0201", outClusters: "000A,0019"
	}

	// simulator metadata
	simulator {
		// status messages
		status "on": "on/off: 1"
		status "off": "on/off: 0"

		// reply messages
		reply "zcl on-off on": "on/off: 1"
		reply "zcl on-off off": "on/off: 0"
	}

	// UI tile definitions
	tiles {
		standardTile("switch", "device.switch", width: 2, height: 2, canChangeIcon: true) {
			state "off", label: 'closed', action: "switch.on", icon: "st.Outdoor.outdoor16", backgroundColor: "#ffffff"
			state "on", label: 'open', action: "switch.off", icon: "st.Outdoor.outdoor16", backgroundColor: "#53a7c0"
		}
        valueTile("battery", "device.battery", decoration: "flat") {
			state "battery", label:'${currentValue}% battery', unit:""
		}
        standardTile("refresh", "device.refresh", decoration: "flat") {
			state "refresh", label:'', action:"refresh.refresh", icon:"st.secondary.refresh"
		}
		main "switch"
		details(["switch","battery","refresh"])
	}
}

// Parse incoming device messages to generate events
def parse(String description) {
	log.info description
    def result1 = zigbee.getEvent(description)

    if(result1) {
        sendEvent(result1)
    } else
	{
    if (description?.startsWith("catchall:")) {
        def value = name == "switch" ? (description?.endsWith(" 1") ? "on" : "off") : null
		def result = createEvent(name: name, value: value)
        def msg = zigbee.parse(description)
		log.debug "Parse returned ${result?.descriptionText}"
		return result
		log.trace msg
		log.trace "data: $msg.data"
	}
	else if (description?.startsWith('read attr -')) {
	return parseReportAttributeMessage(description)
    }
    else {
		def name = description?.startsWith("on/off: ") ? "switch" : null
		def value = name == "switch" ? (description?.endsWith(" 1") ? "on" : "off") : null
		def result = createEvent(name: name, value: value)
		log.debug "Parse returned ${result?.descriptionText}"
		return result
    }    
	}
}

// Commands to device
def on() {
	log.debug "on()"
	sendEvent(name: "switch", value: "on")
	"st cmd 0x${device.deviceNetworkId} 1 6 1 {}"
        'zcl on-off on'
}

def off() {
	log.debug "off()"
	sendEvent(name: "switch", value: "off")
	"st cmd 0x${device.deviceNetworkId} 1 6 0 {}"
        'zcl on-off off'
}

def open() {
	log.debug "on()"
	sendEvent(name: "switch", value: "on")
	"st cmd 0x${device.deviceNetworkId} 1 6 1 {}"
}

def close() {
	log.debug "off()"
	sendEvent(name: "switch", value: "off")
	"st cmd 0x${device.deviceNetworkId} 1 6 0 {}"
}

def refresh() {
	log.debug "sending refresh command"
    "zdo bind 0x${device.deviceNetworkId} 1 1 6 {${device.zigbeeId}} {}"
    zigbee.configureReporting(0x0001, 0x0020, 0x20, 30, 21600, 0x01)
	"st rattr 0x${device.deviceNetworkId} 1 6 0"
    zigbee.readAttribute(0x0001, 0x0020)
    
}

def configure() {

	"zdo bind 0x${device.deviceNetworkId} 1 1 6 {${device.zigbeeId}} {}"
    zigbee.configureReporting(0x0001, 0x0020, 0x20, 30, 21600, 0x01)
    
}

private parseReportAttributeMessage(String description) {
	Map descMap = (description - "read attr - ").split(",").inject([:]) { map, param ->
		def nameAndValue = param.split(":")
		map += [(nameAndValue[0].trim()):nameAndValue[1].trim()]
	}
	log.debug "Desc Map: $descMap"

	def results = []
    
	if (descMap.cluster == "0001" && descMap.attrId == "0020") {
		log.debug "Received battery level report"
		results = createEvent(getBatteryResult(Integer.parseInt(descMap.value, 16)))
	}

	return results
}

private getBatteryResult(rawValue) {
	def linkText = getLinkText(device)

	def result = [name: 'battery']

	def volts = rawValue / 10
	def descriptionText
	if (volts > 3.5) {
		result.descriptionText = "${linkText} battery has too much power (${volts} volts)."
	}
	else {
		def minVolts = 2.1
		def maxVolts = 3.0
		def pct = (volts - minVolts) / (maxVolts - minVolts)
		result.value = Math.min(100, (int) pct * 100)
		result.descriptionText = "${linkText} battery was ${result.value}%"
	}

	return result
}

private String swapEndianHex(String hex) {
	reverseArray(hex.decodeHex()).encodeHex()
}