# Leitura nativa de PDFs de encarte

Bloco 1 da correção do importador.

- Em Android 15+ o importador tenta ler primeiro o texto embutido no PDF digital.
- Se o arquivo não expuser texto útil, o fluxo antigo por PdfRenderer + ML Kit OCR continua funcionando.
- Nenhuma regra ACP, tela de consulta, Firestore ou publicação de ofertas foi alterada neste bloco.
- A meta é preservar códigos internos, EANs, descrições, preços e datas de relatórios tabulares antes de qualquer interpretação por parser/IA.

O Bloco 2 será responsável por interpretar especificamente as colunas dos relatórios Visual Mix (Preço Atual, Promoção Atual, Clube Atual e vigências).
