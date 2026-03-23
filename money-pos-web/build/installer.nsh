; 卸载时的前置宏：专门用来释放被锁定的数据库和 Vana 引擎文件
!macro customUnInstall
  ; 🌟 品牌重塑：精准击杀 Vana 专属引擎，保护客户其他 Java 资产
  ExecWait 'taskkill /f /im vana-java.exe'
  ExecWait 'taskkill /f /im mysqld.exe'

  Sleep 1000
!macroend