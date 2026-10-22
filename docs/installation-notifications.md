# Avisos imediatos de instalação e atualização

O app registra sua atividade em `app_installations`, como antes. Na primeira execução de cada versão também cria, na mesma transação, um evento imutável em `app_installation_events`. Uma prova aleatória fica somente no aparelho; seu SHA-256 identifica o evento e permite solicitar a entrega ao servidor. Clientes não podem ler, enumerar, editar ou apagar esses eventos. As regras exigem que os dados correspondam ao registro de instalação e usem o horário do servidor.

`nrd-installation-event` valida a prova e lê os dados salvos, nunca um texto ou destinatário fornecido pelo cliente. A função reutiliza `FIREBASE_SERVICE_ACCOUNT` e o tópico `master_updates` existentes. Envia FCM data-only de prioridade alta. O app do Mestre reconhece `NEW_INSTALLATION`, exige sessão Mestre e as preferências habilitadas, sem esperar outra consulta ao Firebase para exibir o aviso.

Uma entrega por aparelho/versão é coordenada por `app_installation_deliveries`, acessível somente ao servidor. Uma reserva com prazo e precondição de versão impede envios concorrentes. Falhas liberam a reserva para nova tentativa. O ID da entrega também elimina duplicações no Mestre caso o FCM aceite um envio e a resposta se perca. Os registros de entrega não são expostos aos clientes.

A prova e a confirmação ficam persistidas até o servidor aceitar a entrega. A tentativa inicial ocorre ao iniciar o app. WorkManager é usado apenas para retentar falhas de rede, com tarefa única por versão. `MY_PACKAGE_REPLACED` também agenda o registro de uma atualização. A consulta periódica antiga é cancelada; seu Worker permanece como compatibilidade para tarefas já persistidas.

A preferência no Painel Mestre passa a se chamar **Notificar instalações e atualizações**. A escolha anterior é preservada. Reabrir o app não avisa outra vez; reinstalar a mesma versão no mesmo aparelho também não cria outra entrega. Uma instalação realmente nova requer a primeira abertura do app; atualizações podem registrar pelo broadcast do Android. A entrega depende de conexão, sessão Mestre, preferência e permissão de notificações. Aparelhos com versões anteriores continuam com o comportamento anterior até atualizar.

Validação: testes da função para instalação, atualização, duplicação, concorrência e falhas; regras no emulador Firebase; testes Kotlin de autorização e preferências; compilação assinada. A CI usa OIDC restrito ao fluxo principal para validar as permissões reais do servidor Supabase sem enviar avisos fictícios a usuários. Nenhum segredo administrativo é incluído no APK.
