# Consulta ACP — implementação inicial

Entrada no menu: **Consultar Produtos**. A tela **Confirme** apresenta campos
mascarados e somente leitura. O primeiro acesso de cada aparelho precisa ser
configurado por um administrador NRD na própria tela; não existe senha global
embutida no código, no APK, nas variáveis de build ou nos workflows.

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
  `/api/auth/session`. Sem redirects automáticos ou repetição automática de senha.
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
