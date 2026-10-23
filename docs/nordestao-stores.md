# Nossas Lojas

Aba do menu imediatamente abaixo de Configurações. Lista e detalhes vêm do cadastro de lojas do Nordestão Pra Você, via `nrd-stores`, sem catálogo fixo de unidades no APK. Pesquisa sem acentos por nome, cidade, bairro e rua. A ficha mostra endereço/CEP, horários quando fornecidos, situação informada pela fonte, identificação e capacidade quando a fonte permite, localização no mapa e telefones via discador (não exige permissão de ligação).

Fonte validada em 23/10/2026: `/api/v1/stores` respondeu 12 lojas, incluindo Doze Anos e Abolição IV. Todas tinham `businessHours` vazio, telefone e endereço com coordenadas. Nenhum campo WhatsApp veio na resposta; não se presume que um telefone seja WhatsApp. Horários vazios são informados como ausentes, sem inventar abertura/fechamento. `isOpen` é explicitamente descrito como situação na última consulta, não calculado por relógio ou inferido dos horários.

A função preserva a verificação JWT e reutiliza a configuração existente de tenant no servidor; credenciais nunca são copiadas para o Android. Publica apenas campos comerciais de lojas, sem campos arbitrários da origem. Não altera encartes, ACP ou configurações de visibilidade do treinamento. Sem novas tabelas, cron ou gravação periódica em banco. Cache da função de cinco minutos com compartilhamento de consulta em andamento e token em memória; cabeçalho HTTP de um minuto.

O app consulta ao abrir/retornar e a cada minuto enquanto a tela está em primeiro plano, além do botão Atualizar. Considerando o cache, mudanças da origem normalmente aparecem na próxima atualização do cache (aproximadamente até seis minutos com a tela aberta, além de latência de rede). Não é sincronização por webhook. Uma resposta válida substitui a lista inteira, incluindo remoções e lista vazia. Falha mantém somente o último resultado da sessão, com aviso explícito e horário da consulta; primeiro carregamento com falha mostra erro/tentar novamente. Não há promessa de atualização offline.

Validação: cinco testes Node do contrato público/filtros/validação; testes Android com amostra observada das 12 lojas para parse, pesquisa, remoção e edição da origem; testes de UI de lista/detalhe/erro. Todos incluídos no CI existente.

Comportamentos presentes no APK e reproduzidos no NRD: busca, lista/mapa com marcadores e detalhe, estrela de loja preferida, filtro da preferida e ordenação Mais próxima. A preferência fica no NRD e não altera a conta oficial ou os filtros ACP/encartes. Localização aproximada é pedida apenas ao tocar Mais próxima, usada em memória para distância em linha reta e não enviada ao backend de lojas. Sem permissão/GPS disponível, lista e mapa continuam utilizáveis. O mapa usa Leaflet 1.9.4 com integridade fixa, OpenStreetMap e atribuição visível, cache padrão do WebView, sem download em lote e identificação NRD no User-Agent. Texto da origem vira DOM textContent, sem executar HTML da loja ou ponte JavaScript nativa. Cartão financeiro, campanhas e perfil oficial não são alterados.

## Recuperação de falhas de carregamento

O endpoint mantém a última resposta oficial válida em `nrd_stores_cache`, acessível somente ao servidor (RLS habilitada e permissões de anon/authenticated revogadas). Após reinício do worker, essa resposta permite exibir as lojas imediatamente. Dados vencidos são renovados em segundo plano; falhas preservam a data real da última consulta e aplicam espera de 60 segundos entre tentativas. Uma resposta oficial vazia continua substituindo a lista. O prazo da consulta de lojas começa somente após a autenticação.

O filtro Preferida mostra apenas a unidade marcada com estrela; desative-o para visualizar todas as lojas.

## Mapa e horários (correção Android)

Leaflet 1.9.4 (JS/CSS e licença BSD) incluído no APK, com SHA-256 conferido contra os hashes SRI oficiais. Não há script ou estilo de CDN ao abrir o mapa. Marcadores usam elementos locais, sem download de ícones. O fundo continua usando tiles HTTPS do OpenStreetMap, com atribuição e User-Agent do NRD. WebView ocupa tamanho explícito e o mapa recalcula o tamanho ao mudar o espaço disponível.

Horários do encarte fornecido em 23/10/2026 são fallback por nome exato normalizado: Lagoa Nova, Cidade Jardim, Santa Catarina, Tirol, Nova Parnamirim e Ponta Negra abrem das 07h às 22h todos os dias, inclusive feriados. Alecrim, Petrópolis, Igapó/São Gonçalo e Rota do Sol abrem segunda a sábado das 07h às 22h, e domingos e feriados das 07h às 20h. O usuário confirmou Doze Anos e Abolição IV: segunda a sábado, 07h às 22h; domingos, feriados e feriados locais, 07h às 20h. Horários não vazios da API oficial têm prioridade.

## Localização e gestos

A localização aproximada já autorizada pelo usuário é enviada somente à WebView local, onde um marcador azul identifica a posição. O botão Minha localização solicita uma posição e centraliza novamente; conceder permissão continua sem enviar coordenadas ao backend. Atualizações de lojas usam JavaScript na página existente e preservam a câmera e o zoom, sem recarregar a WebView. O menu lateral não intercepta arrastos nessa rota e a WebView mantém a sequência de toque até UP/CANCEL. A ordenação por distância permanece disponível na lista.
