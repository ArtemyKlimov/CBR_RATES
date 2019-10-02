echo "starting"
BRKNAME=BROKER1
QPREFIX=TIMER1
TIMER_SC=TIMER1
echo "creating a Timer configurable service for broker $BRKNAME, timer $TIMER_SC with $QPREFIX prefix"
mqsicreateconfigurableservice $BRKNAME -c Timer -o $TIMER_SC -n queuePrefix -v $QPREFIX