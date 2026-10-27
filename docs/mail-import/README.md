# Relatórios encaminhados por Gmail

## Escopo

Somente lojas diferentes de 0012. Cidade Jardim conserva ACP, conferência e validade existentes. Os documentos CENTRO_NNNN.pdf e CLUBE_NNNN.pdf das outras lojas são lidos pelo mesmo parser Visual Mix, sem consulta ACP. De/Por e Clube são publicados com os preços e datas explícitos do relatório. Outras regras não reconhecidas permanecem indicadas como pendências, sem inventar condições.

## Ativação pelo dono do Gmail

1. Instale a versão que contém Relatórios por e-mail e abra o painel Mestre → Habilitar lojas.
2. Em Configurar Gmail, informe a caixa exclusiva e gere o conector. A geração substitui qualquer chave anterior.
3. Crie um projeto **privado** em https://script.google.com/home/start, usando essa mesma conta Gmail.
4. Copie o código pelo botão do NRD e cole em Código.gs. Nas configurações do projeto, habilite Mostrar o arquivo de manifesto appsscript.json. Copie o manifesto pelo outro botão e substitua esse arquivo no projeto.
5. Salve, selecione ativarNRD e execute. Autorize a leitura de Gmail, o envio dos anexos ao endpoint NRD e o acionador periódico. O aplicativo não pede senha. Não implante como aplicativo Web nem publique o projeto. A conta é conferida por getProfile antes da leitura.
6. Encaminhe uma mensagem com os relatórios para a caixa exclusiva. O conector verifica os últimos 30 dias a cada 15 minutos, mas o acionador roda a cada minuto para consultar pedidos do botão Buscar documentos recebidos. Na primeira instalação ou após atualizar o conector, execute ativarNRD uma vez para substituir o acionador antigo. Não é necessário executar receberRelatoriosNRD manualmente. Se o Google pedir habilitar Gmail API, adicione Gmail em Serviços do projeto; o manifesto inclui o serviço avançado v1.
7. No NRD, Buscar documentos recebidos → Ler todos → Revisar. Marque os documentos conferidos e publique em lote. O botão também solicita ao conector uma leitura do Gmail e mostra confirmação ou falha; a resposta ocorre no próximo acionador e pode demorar se houver muitas páginas. O recebimento é automático; a revisão/publicação continua nas mãos do Mestre. Para publicação totalmente automática com o app fechado, ainda seria necessário um parser seguro no servidor e regras de aprovação específicas; o NRD não publica preços extraídos sem conferência. A leitura dos PDFs acontece enquanto essa tela do app está aberta; se interrompida, os PDFs continuam na fila e podem ser lidos novamente.

Se houver um documento vencido ou que não deva ser publicado, use Descartar da fila; o original continua no Gmail.

A autorização real da conta e um e-mail real são necessários para o teste ponta a ponta. Uma caixa informada por texto não significa que a integração já foi autorizada. A conexão Gmail do ChatGPT não conecta automaticamente esse serviço ao NRD.

## Regras de publicação

- A unidade vem do nome do anexo e precisa ser conferida no documento durante a revisão. Não é alegada validação de loja pelo ACP ou por cabeçalho que não tenha sido lido.
- Relatórios de alterações são deltas. A publicação preserva outras ofertas não vencidas, mantém campanhas futuras junto das atuais e atualiza a mesma identidade/produto/família/data inicial. Entre campanhas vigentes sobrepostas, a data inicial mais recente prevalece.
- De/Por e Clube têm identidades separadas. Os leitores antigos não conhecem o novo campo family; atualize os usuários para a versão nova antes de publicar Clube para outras lojas.
- Datas faltantes impedem a publicação pela fila. Linhas inválidas/não suportadas são mostradas e ficam fora da publicação. O parser conserva os avisos de extração; revisão de um PDF real é necessária antes de aprovar comercialmente uma loja.
- Repetir a publicação após uma interrupção não duplica os registros de mesma identidade e início. O processo de publicação é serializado no app. Uma falha no ACP preserva o cache vigente da Cidade Jardim e não impede a atualização independente das outras lojas. Publicação concorrente por vários dispositivos Mestre ainda deve ser evitada; o merge de deltas não é uma transação distribuída entre dispositivos.

## Armazenamento e autorização

nrd-mail-import verifica JWT Firebase RS256/issuer/audience e Mestre para configurar, listar, ler e concluir. O conector recebe uma capacidade aleatória de 256 bits, cujo hash SHA-256 fica no servidor. Ela só pode enviar anexos, nunca consultar ou publicar ofertas. Não há chave service_role, senha Gmail ou token Google no APK ou script.

Tabelas privadas com RLS habilitado e privilégios anon/authenticated revogados. A função nrd_mail_enqueue é SECURITY INVOKER, executável apenas por service_role, com trava transacional para duplicatas e limite de fila. A assinatura PDF e o nome são validados; loja 0012 é ignorada em dois pontos. Hash da unidade + conteúdo identifica duplicatas.

PDF por anexo: até 3 MiB. Fila: até 250 anexos pendentes e 64 MiB em base64. Na próxima entrada, anexos não revisados há mais de 14 dias têm o conteúdo expirado. Após publicação/descarte, o PDF é removido e o recibo é mantido para deduplicação. Os originais continuam no Gmail. Falhas não marcam a mensagem como processada. O conector limita cada execução a quatro minutos e usa paginação persistente.

Auditoria confirmou que as tabelas não podem ser lidas por anon/authenticated e que anon não pode executar a função SQL. Aviso RLS sem policy é intencional nessas tabelas somente servidor: https://supabase.com/docs/guides/database/database-linter?lint=0008_rls_enabled_no_policy . Os avisos preexistentes do projeto não foram alterados neste trabalho.

## Validação

Node: autenticação, capacidade sem permissão de leitura/publicação, nomes inválidos, assinatura PDF, duplicatas, exclusão 0012, script percorrendo anexos aninhados e leitura somente Gmail. SQL em transação revertida: inserção, duplicata e remoção do PDF. Android: parser/regressões existentes, Clube independente de ACP, preservação de famílias, campanhas atuais/futuras e idempotência do merge. O teste real Gmail depende da autorização do proprietário.
