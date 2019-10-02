set BRKNAME=BRK01
set QPREFIX=TIMER1
set TIMER_SC=TIMER1
C:\Windows\System32\cmd.exe /K "cd C:\Program Files\IBM\IIB\10.0.0.*\ & IIB.cmd" ^
mqsicreateconfigurableservice %BRKNAME% -c Timer -o %TIMER_SC% -n queuePrefix -v %QPREFIX%


  
