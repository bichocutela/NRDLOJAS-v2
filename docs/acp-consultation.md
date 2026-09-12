# Consulta ACP — implementação inicial

Entrada no menu: **Consultar Produtos**. A tela **Confirme** apresenta campos
mascarados e somente leitura. O primeiro acesso de cada aparelho precisa ser
configurado por um administrador somente em builds sem credencial incorporada.
A build de distribuição recebe ACP_LOGIN e ACP_PASSWORD dos Actions secrets e
incorpora esses valores ao APK, conforme solicitado pelo responsável. Os valores
não ficam no código versionado, mas podem ser extraídos do APK.

Credenciais e cookies ACP são cifrados com AES-GCM e uma chave Android Keystore,
em `noBackupFilesDir`. O acesso ACP é independente de Firebase, Nossa Gente e
Supabase. Remover os dados do aplicativo remove a configuração local.

## Blocos

1. Entrada, confirmação, configuração administrativa e sessão NextAuth isolada.
2. Consulta GET por `barCode`, `code` ou `description`, filtro por categorias
   reais da ACP, paginação e diálogo de condições cadastradas.
3. CameraX + ML Kit já presentes no projeto: câmera traseira, permissão,
   reconhecimento único e busca automática. As imagens são processadas no
   aparelho; não são salvas nem enviadas à ACP. Somente o código reconhecido
   é usado na pesquisa. Ler com câmera redefine o filtro para Todas.

## Contrato identificado, ainda sujeito à validação ao vivo

- NextAuth: GET `/api/auth/csrf`, POST `/api/auth/callback/credentials` com
  `login`, `password`, `csrfToken`, `callbackUrl`, `json=true`, seguido de GET
  `/api/auth/session`. Sem redirects automáticos. Após acesso confirmado/restaurado, um 401 permite uma
  única autenticação normal e repetição da consulta; 403, 429 e falhas de rede não.
- API: `https://api.acp.app.br/api/v1/`; quando `user.proxyEnable` estiver ativo,
  usa o proxy do tenant `/api/proxy/api/v1/`. Bearer obtido de `user.accessToken`.
- Consulta: GET `Product/all` com `pageIndex` base zero, `pageSize`, filtro
  selecionado e opcional `productCategoryIds`.
- Categorias: GET `ProductCategory/all`, com paginação.
- Não há chamadas de atualização, impressão, sincronização ou exclusão.
- Os exemplos dos testes são sintéticos e verificam o contrato inspecionado
  nos scripts públicos; não devem ser apresentados como respostas reais.

## Preços e limites desta etapa

O diálogo separa `value`, `previousValue`, `clubValue`, atacado, Leve/Pague,
segunda unidade e cashback. Não aplica descontos cumulativos nem escolhe o menor
preço de modalidades distintas. Equivalente Leve/Pague usa o valor principal e
exige quantidades inteiras válidas. Campos ausentes não viram preço zero.

**Validade não confirmada** é intencional: o frontend ACP tem fallback de sete
dias para a prévia. `dueDate` é vencimento e não validade comercial. Campanhas
podem substituir valores do produto; sua precedência não está implementada.
Categoria e valores preenchidos não bastam para afirmar promoção vigente.

`unitLimitPerCPF` é mostrado como limite cadastrado, sem presumir consulta de
elegibilidade do cliente. Não é coletado CPF nesta funcionalidade.

## Verificação

Os testes de autenticação usam respostas HTTP sintéticas, sem rede nem credenciais
reais. Cobrem a sequência CSRF/callback/sessão, reaproveitamento da sessão, recusa
de login, token ausente ou inválido, limpeza após 401, seleção do proxy, parâmetros
repetidos e bloqueio de redirects. Eles não substituem a validação no servidor ACP.

Workflow `acp-validation.yml`: compila a aplicação e executa testes ACP sem
processar configuração de produção do Google Services, gerar release ou publicar
APK. A branch de trabalho evita o workflow de publicação da main.

Próxima etapa acordada: validar login real no Android, JSON autenticado, campos
nulos, expiração/renovação, campanhas concorrentes, vigências, atualização de
preços e leitura de câmera em aparelho físico, antes da publicação de uma versão.

## Validação no navegador — 11/09/2026

Login realizado com sucesso pelo formulário seguro da ACP. A página autenticada
identificou Nordestão 12 e permitiu abrir `/print-template`. Isso confirma o login
no navegador, mas não valida a autenticação nativa implementada no Android.

No template Promoção Clube, código 32, a busca pelo EAN `7891000412855` retornou:

| Informação | Valor exibido na ACP |
| --- | --- |
| Produto | Achocolatado em Pó Nescau Lata 350g |
| Código interno | 2038420 |
| Código de barras | 7891000412855 |
| Valor | R$ 14,29 |
| Valor Clube de Vantagens | R$ 9,99 |
| Categorias | Varejo; Clube de Vantagens |

O campo de validade estava vazio, enquanto a prévia mostrava 18/09/2026.
Esse comportamento é compatível com o fallback de sete dias identificado no
frontend; a data da prévia não deve ser tratada como vigência comercial confirmada.
Não foi feita consulta de elegibilidade por CPF, nem capturado JSON autenticado.
Não foram acionados Salvar Tudo ou Imprimir.

A página inicial também informou que este acesso será descontinuado e orientou
obter novo acesso com o responsável indicado pela ACP. Prazo, novo endereço e
compatibilidade da API ainda não foram confirmados.

## Roteiro de teste no Android

1. Instalar a build que contenha `feat/acp-product-consultation`. Entrar no NRD
   como administrador e abrir **Consultar Produtos**. Configurar as credenciais
   ACP uma vez no aparelho, pela própria tela. Não colocá-las em issues ou logs.
2. Confirmar que os campos ficam mascarados e bloqueados na tela **Confirme**;
   tocar em Entrar e verificar se a consulta abre. Registrar a mensagem de erro,
   caso falhe, sem compartilhar senha, cookies ou tokens.
3. Buscar `7891000412855` por código de barras e `2038420` por código interno.
   Buscar também Nescau por descrição. Comparar identificação e preços com a
   ACP no mesmo momento: os valores acima são uma observação, não preços fixos.
4. Abrir o produto e conferir a separação de preço normal e clube e o aviso
   **Validade não confirmada**. Não interpretar falta de dados como ausência de
   promoção nem como preço zero.
5. Permitir câmera, apontar para um código de barras e confirmar preenchimento
   e busca automática únicos. Testar também cancelar, negar a permissão e
   continuar pela digitação manual.
6. Fechar e reabrir o app; verificar a persistência do acesso. Testar consulta
   sem internet e conferir se o erro permite nova tentativa. Expiração real da
   sessão e renovação continuam pendentes até serem observadas.
7. Conferir Home, busca existente, Promoções e navegação do NRD. Registrar modelo
   do aparelho, versão Android, build testada e passos de qualquer falha.

Campanhas simultâneas, campos nulos reais, atualização dos preços e vigência
comercial continuam sem validação ao vivo. A implementação não resolve a
precedência entre campanhas nesta etapa.

## Acesso pronto no APK

Cadastrar uma única vez os repository Actions secrets `ACP_LOGIN` e `ACP_PASSWORD`
antes da build de distribuição. O workflow interrompe a build se estiverem ausentes.
Não há backend novo. Em builds configuradas, qualquer usuário do NRD vê os campos
mascarados e bloqueados e toca em Entrar; não precisa configurar cada aparelho.
A tela primeiro verifica cookies existentes sem enviar senha; sem sessão válida,
o primeiro login começa ao tocar no botão. A credencial da build tem prioridade
sobre uma configuração antiga local quando for necessário autenticar novamente.
Sessões válidas são reaproveitadas. A senha incorporada é recuperável por quem
inspecionar o APK; os asteriscos são apenas apresentação visual.

O conector disponível nesta sessão não permite cadastrar Actions secrets.
O responsável precisa preencher esses dois valores nas configurações do GitHub.

## Cartões inspirados nos cartazes

Consulta e balão usam cartões amarelos, cabeçalho azul para Clube e vermelho
para outras condições, preço destacado e faixa de cores. Sem fotos, QR,
parcelamento ou datas copiadas das referências. De/Por usa previousValue como
referência riscada; Clube e Atacado usam value positivo como preço normal
cadastrado, sem confundi-lo com previousValue. Referência ausente não é inventada.
Segunda unidade exibe percentual, sem calcular um preço médio cuja base não foi
confirmada. Várias condições permanecem separadas; a estética não confirma vigência.


## Sessão e atualização sob demanda

A entrada restaura cookies existentes e consulta a sessão no servidor. Consultas
serializadas impedem renovações concorrentes. Após uma confirmação ou restauração
bem-sucedida, um 401 permite somente um novo login normal e uma repetição do GET.
Falha nessa recuperação encerra a autorização para novas tentativas automáticas.
Não há tentativa de prolongar a validade imposta pelo servidor.

Abrir uma ficha refaz Product/all pelos códigos selecionados, exige identidade
exata e rejeita duplicatas. A ficha não apresenta o preço anterior como recém
consultado durante carregamento ou erro. Há atualização manual na busca e na
ficha, com horário da consulta (não é data de atualização comercial da ACP).

Testes HTTP de recuperação e atualização são sintéticos: não comprovam que a ACP
aceitará renovar uma sessão real. Persistência e câmera ainda exigem aparelho.
