// =========== roboLib ============
// ===  *** BARTOSZ NAWROT ***  ===
// ================================
#ifndef RADIOCALIBACTION_HPP
#define RADIOCALIBACTION_HPP

#include "ISkyDeviceAction.hpp"

#include <atomic>

class RadioCalibAction : public ISkyDeviceAction
{
public:
    RadioCalibAction(Listener* const _listener);

    void start(void) override;

    IMessage::MessageType getExpectedControlMessageType(void) const override;

    bool isActionDone(void) const override;

    Type getType(void) const override;

    std::string getStateName(void) const override;

private:
    enum State
    {
        IDLE,
        INITIAL_COMMAND,
        CALIBRATION_COMMAND,
        CALIBRATION_RESPONSE,
        BREAKING,
        CHECK,
        FINAL_COMMAND,
        CALIBRATION_RECEPTION
    };

    std::atomic<State> state;

    void handleReception(const IMessage& message) override;
    void handleSignalReception(const Parameter parameter) override;
    void handleUserEvent(const PilotEvent& event) override;

    int current;
};

#endif // RADIOCALIBACTION_HPP
