# Plano de separação Firebase / Supabase por instalação

## Contrato exigido
- **Supabase para todos os aparelhos:** Consultar Preços, Promoções comerciais (incluindo cache e sincronização ACP), login de Ofertas, credenciais ACP/ACP Tech e processamento de notificações comerciais. A entrega push continua via Firebase Cloud Messaging (FCM).
- **Firebase para instalações novas:** configurações não comerciais do Painel Mestre, metadados de Encartes, Nossas Lojas, preferências e Treinamento. Notificações comerciais continuam processadas no Supabase.
- **Instalações anteriores à migração:** conservar o caminho legado para serviços ainda hospedados no Supabase; não remover endpoints ou dados enquanto existirem clientes dependentes.
- Não duplicar PDFs/imagens no Firestore. Manter URLs HTTPS da fonte/Drive e validar domínios.
- Credenciais de fornecedores e funções administrativas nunca devem ser expostas em documentos públicos ou no APK.

## Constatações verificadas no repositório
- `EncartesRepository` lê `/functions/v1/nrd-encartes`; o servidor consulta Idever e protege credenciais no Vault.
- `NordestaoStoresRepository` usa `nrd-stores`; a função autentica e mantém cache de lojas.
- `DeviceInstallationTracker` já grava `app_installations/{sha256(packageName:ANDROID_ID)}` no Firestore com `firstSeenAt`. Também envia um evento de instalação para uma função Supabase. Não há evidência de que todo aparelho antigo esteja registrado.
- O identificador Android pode mudar após reinstalação/restauração e não deve ser tratado como prova infalível de instalação histórica.
- Preferências locais continuam no DataStore; gravação opcional no Firebase foi introduzida na PR #266.

## Etapas antes de ativar o roteamento
1. Definir um instante de corte **no servidor** e congelar a lista de instalações legadas identificáveis; instalações sem prova confiável devem usar fallback conservador, não ser migradas silenciosamente.
2. Implementar documento de capacidade/roteamento por instalação, assinado/validado no servidor, com versão e fallback; **não confiar em uma flag escrita pelo próprio cliente**.
3. Preservar as publicações não comerciais do Painel Mestre já no Firebase (Home, aparência, temas, fundos, treinamento e políticas globais). Não migrar os logins de Ofertas, ACP ou ACP Tech nem o Vault comercial; revisar somente configurações ainda não migradas.
4. Publicar snapshots pequenos e versionados de Encartes e Nossas Lojas no Firebase. Uma função backend protegida continua consultando Idever; distribuir dados públicos via CDN/Hosting ou documentos pequenos e cache local. Usar refresh incremental e limites de leitura.
5. Implementar Android dual-read por serviço: legado para instalações confirmadas, Firebase para novas; fallback explícito sem sobrescrever dados ou misturar tenants.
6. Testar regras Firestore (usuário, mestre, público), compatibilidade com APK antigo, novas instalações, reinstalação, ausência de rede, expiração/remoção de encartes, troca de loja e rollback.
7. Publicar regras/serviços antes do APK, acompanhar custo e só depois reduzir endpoints legados. Nunca excluir dados do Supabase automaticamente.

## Não fazer
- Não copiar os ~63 mil produtos comerciais para documentos Firestore.
- Não migrar tokens, senhas ou chaves do Mestre para documentos legíveis por clientes.
- Não usar somente a versão do APK como critério: aparelhos antigos também atualizam.
- Não prometer que um novo APK altera o comportamento de APKs antigos sem atualização.
