#include "ISkyDeviceMonitor.hpp"

ISkyDeviceMonitor::~ISkyDeviceMonitor()
{
}

void ISkyDeviceMonitor::notifyUavEvent(const UavEvent* const event)
{
    notifyUavEvent(std::unique_ptr<const UavEvent>(event));
}

double ISkyDeviceMonitor::getControlDataSendingFreq(void)
{
    // by default return 25 Hz's
    return 25.0;
}
