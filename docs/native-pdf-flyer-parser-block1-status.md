# Status do Bloco 1

Implementado na branch `feat/native-pdf-flyer-parser`.

O importador de PDF agora tenta aproveitar o texto nativo do documento em Android 15+ antes de cair no OCR por imagem. Se a extração nativa falhar ou retornar pouco texto, o OCR existente continua como fallback.

Neste bloco não foram alterados ACP, Consultar Produtos, regras comerciais, Firestore ou aprovação do Mestre.
