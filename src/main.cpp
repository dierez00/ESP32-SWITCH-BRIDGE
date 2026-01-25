#include <Arduino.h>
#include "SwitchController.h"

struct MacroStep {
    uint16_t bu1;
    uint16_t bu2;
    uint16_t bu3;
    uint8_t lx;
    uint8_t ly;
    uint8_t rx;
    uint8_t ry;
    int duration;
};

/*  Macro declaration
    Format: {button_byte_1, button_byte_2, button_byte_3, stick_left_x, stick_left_y, stick_right_x, stick_right_y, duration (in ms)}
    Stick values: 0 = LEFT/DOWN -> 128 = NEUTRAL -> 255 = RIGHT/UP
    Multiple button selection: (BTN_A|BTN_B) */
MacroStep macroSequence[] = {
    { 0, BTN_PLUS, 0, 128, 128, 128, 128, 100 },
    { 0, 0, 0, 128, 128, 128, 128, 200 },
    { 0, 0, 0, 150, 180, 128, 128, 200 },
    { 0, 0, 0, 128, 128, 128, 128, 200 },
    { BTN_A, 0, 0, 128, 128, 128, 128, 100 },
    { 0, 0, 0, 128, 128, 128, 128, 1100 },
    { BTN_A, 0, 0, 128, 128, 128, 128, 100 },
    { 0, 0, 0, 128, 128, 128, 128, 9000 }
};

/*  Default values */
SwitchController controller;
const int TRIGGER_BUTTON = 4;
bool macroActive = false;

const int totalSteps = sizeof(macroSequence) / sizeof(MacroStep);

int currentStepIndex = 0;
unsigned long stepStartTime = 0;
unsigned long lastReportTime = 0;

int lastButtonState = HIGH;
unsigned long lastDebounceTime = 0;
unsigned long debounceDelay = 50;

/*  Set ESP mac-address to specific value.
    First 3 values are Nintendo specific and need to stay the same.
    Last 3 values can be varied.
    CAVE!:  resulting BT mac address will be -> base mac address + 2
            (e.g.: device mac = D4:F0:57:12:34:56 --> bt mac = D4:F0:57:12:34:58) */
void setDMacAddress() {
    uint8_t dmac_addr[6] = {0xD4, 0xF0, 0x57, 0x45, 0x56, 0x33};
    esp_base_mac_addr_set(dmac_addr);
}

/*  Arduino setup logic
    Important to set the mac-address first!
    Choose and change controller type here:
    (CT_PRO_CONTROLLER, CT_JOYCON_L, CT_JOYCON_R) */
void setup() {
    setDMacAddress();
    Serial.begin(115200);
    delay(1000); 
    Serial.println("System startup...");
    pinMode(TRIGGER_BUTTON, INPUT_PULLUP);
    
    if(controller.begin(CT_PRO_CONTROLLER)) {
        Serial.println("Controller ready!");
    } else {
        Serial.println("Controller error!");
    }
}

/*  Handling of external button to start/stop the macros */
void handlePhysicalButton() {
    int reading = digitalRead(TRIGGER_BUTTON);

    if (reading != lastButtonState) {
        lastDebounceTime = millis();
    }

    if ((millis() - lastDebounceTime) > debounceDelay) {
        static int stableState = HIGH;
        if (reading != stableState) {
            stableState = reading;
            if (stableState == LOW) {
                macroActive = !macroActive;
                
                if (macroActive) {
                    Serial.println("Macro activated!");
                    currentStepIndex = 0;
                    stepStartTime = millis();
                } else {
                    Serial.println("Macro deactivated!");
                }
            }
        }
    }
    lastButtonState = reading;
}

/*  Main loop */
void loop() {
    handlePhysicalButton();
    if (controller.isConnected()) {
        unsigned long currentMillis = millis();
        // Wait for handshake, then send regular reports to keep connection active
        if (controller.isHandshakeComplete()) {
            if (currentMillis - lastReportTime > 15) {
                // Macro logic
                if (macroActive) {
                    if (currentMillis - stepStartTime >= macroSequence[currentStepIndex].duration) {
                        currentStepIndex++;
                        stepStartTime = currentMillis;
                        if (currentStepIndex >= totalSteps) {
                            currentStepIndex = 0;
                        }
                    }
                    MacroStep currentStep = macroSequence[currentStepIndex];
                    controller.setButtons(currentStep.bu1, currentStep.bu2, currentStep.bu3);
                    controller.setSticks(currentStep.lx, currentStep.ly, currentStep.rx, currentStep.ry);
                } else {
                    controller.setButtons(0, 0, 0);
                    controller.setSticks(128, 128, 128, 128);
                }
                controller.sendReport();
                lastReportTime = currentMillis;
            }
        }else {
            if (currentMillis - lastReportTime > 1000) {
                Serial.println("Waiting for handshake...");
                lastReportTime = currentMillis;
            }
        }
    }
    delay(1);
}