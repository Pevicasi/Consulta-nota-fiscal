# NF-e Fácil 1.4.0 — scanner, tabela compacta e IPI

Implementação das solicitações da issue #1, comentários 6447774732, 6447883343, 6447928390 e esclarecimento 6448141543. Pacote original, APK universal debug automático pessoal, sem Secrets de assinatura e sem remoção de ABIs.

## Interface Android

`android-invoice.js` é injetado somente no domínio appassets e página local de notas. Nenhum HTML/JS/CSS público foi editado nesta atualização: os quatro HTMLs e `fiscal-cost.js` continuam byte a byte iguais ao commit f15b3bb. O motor fiscal anterior é preservado.

A tabela começa com oito campos principais; 17 estão disponíveis, incluindo IPI por unidade. Ordem antiga de 16 campos migra sem perder a ordem, com IPI visível inicialmente junto a ST. Ordem e visibilidade passam a ser persistidas. As linhas não contêm formulários, parágrafos XML ou explicações: apenas nome e ação discreta. Conversão e auditoria aparecem em diálogo separado sob demanda, que retorna à nota e posição anterior. CSV acrescenta IPI unitário/base sem deslocar os campos existentes; impressão mantém auditoria detalhada. IPI ausente é diferente de zero explícito. Conversão pendente exibe valores comerciais rotulados; não inventa venda por peça.

## Scanner

Busca colorida percorre a imagem com cantos e linha de varredura: é efeito de busca, nunca posição detectada. Azul indica candidato com geometria real; verde indica geometria validada; amarelo diferencia múltiplas escolhas. Alvos são recebidos do ML Kit e mapeados pelo CameraX, nunca inferidos da animação. O movimento respeita `ValueAnimator.areAnimatorsEnabled`, pausa/retomada, foto e confirmação. Limite de 30 fps; sem loop quando oculto/inativo.

Primeiro passe usa diretamente InputImage/media Image. Chave válida sem candidatos ambíguos retorna sem Bitmap/ROIs. Bitmap só é convertido quando detecções potenciais ou geometria mesclada justificam refinamento. Refinamento limitado a oito ROIs e orçamento de dois segundos entre chamadas (uma chamada ML Kit em execução não pode ser interrompida). Não faz varredura cega sem candidato. Ambiguidades continuam exigindo escolha explícita. OCR é fallback com intervalo, nunca aguarda OCR após barcode válido. Foto/recorte também retorna sem OCR após barcode válido.

Frames têm conclusão única em sucesso/falha/cancelamento; Bitmap é mantido até encerrar os consumidores. Logs `NfeScanTiming` contêm somente etapa, milissegundos e número de ROIs executados, sem chave, imagem, URI ou coordenadas pessoais. Mantidos resolução, foco e zoom: não há evidência física para prometer melhoria neles.

## Testes e limites

31 testes JS (incluindo três novos de interface); 11 Java (três novos de política/orçamento/liberação); cinco Python; 35 instrumentados Android. Novos testes usam 15 produtos UN/CX/FD/PCT/KG, IPI positivo/zero/ausente, editor separado, persistência, 90/100/140/160%, claro/escuro/automático e retrato/paisagem. Mantidos testes de SAF/XML múltiplo, desconto, cálculos, CSV, scanner, OCR, múltiplos códigos e transformações de rotação/espelho CameraX.

Benchmark usa o leitor anterior do commit f15b3bb somente no APK de testes, contra a implementação atual e o mesmo modelo ML Kit aquecido. Três pares por sete variantes sintéticas: normal, inclinado, pequeno, fundo escuro, baixo contraste, linha de dobra e múltiplos CODE128. Ordem de execução alternada. `scanner-benchmark.json` registra números observados e conversões; não contém chaves. `scanner-animation-frames.json` e PNGs sequenciais comprovam busca em movimento e transição para retângulo do código realmente decodificado de um quadro sintético exibido na área de preview. Esse ensaio não é um DANFE real na câmera física. Não prometer latência no telefone do proprietário; os números finais devem ser lidos no artefato concluído do CI e registrados na issue.

Política de entrega atual: publicar/validar o APK novo, conferir download público e só então remover APKs anteriores de releases/artefatos, sem reescrever histórico, apagar site ou fontes necessárias. Não recriar APK histórico automaticamente.
