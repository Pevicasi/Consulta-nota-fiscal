# Assinatura permanente e migração segura

## Diagnóstico confirmado

Os APKs 1.0.1 tinham pacote `br.com.pevicasi.nfefacil` e `versionCode=2`, mas certificados diferentes:

- Build `a3e9f3b`: `f41a773c92dad15beca1ad404a1fc1c2ff0c703534193cf8bcea19180866dc3d`.
- Build aprovado `d068e38`: `e07eda16ef5de0e2600c57db805f280dfa68a696b4d14113c4359e94064223c5`.

São certificados **Android Debug** gerados em runners diferentes. Aumentar versionCode ou trocar o nome do APK não permite atualizar um pacote com assinatura incompatível. Não é possível recuperar a chave privada de um certificado ou do APK. Não há rotação/linhagem válida sem acesso à chave anterior e suporte dos dispositivos; este projeto não inventa uma linhagem.

## Separação a partir de 1.1.0

- CI normal: `NF-e Fácil (teste)`, pacote `br.com.pevicasi.nfefacil.qa`, versão com sufixo `-qa`. É um APK de testes, com certificado debug, dados separados e sem promessa de atualização estável. Instala lado a lado com o app antigo e não o substitui.
- Distribuição: `NF-e Fácil`, pacote original `br.com.pevicasi.nfefacil`, build **release não depurável**, assinado pela chave permanente do responsável. Nenhum fallback para assinatura debug/temporária. Sem Secrets completos e certificado aprovado, o build de distribuição falha.

## Configuração que exige o responsável

Reutilize a chave permanente existente, caso esteja sob sua guarda. Se não existir, crie-a **uma única vez em uma máquina confiável sob seu controle**, usando o JDK/keytool oficial. Não envie o arquivo, base64 ou senhas no chat. Não publique chave/senhas no Git, Issues, logs, caches ou artefatos.

Exemplo para criação local (com prompts interativos de senha e identificação; substitua o caminho por uma pasta privada):

```sh
umask 077
keytool -genkeypair -keystore /caminho/privado/nfe-distribution.p12 \
  -storetype PKCS12 -alias nfe-distribution -keyalg RSA -keysize 3072 \
  -sigalg SHA256withRSA -validity 10000
keytool -list -v -keystore /caminho/privado/nfe-distribution.p12 -alias nfe-distribution
```

Os dados do certificado são públicos; evite incluir dados pessoais desnecessários. Use senha forte e, para PKCS12 criado por keytool, a mesma senha para armazenamento/chave. Mantenha ao menos duas cópias de segurança cifradas, fora do repositório/runner, e as senhas em um gerenciador seguro. Perder a chave pode impedir atualizações futuras. O certificado SHA-256 é público; registre e aprove sua impressão digital por um canal confiável.

No GitHub, configure preferencialmente o environment **`android-distribution`**, com aprovação de responsável e restrição à branch main. Também são aceitos Secrets do repositório. É necessário provisionar:

| Tipo | Nome | Conteúdo inserido seguramente no GitHub |
|---|---|---|
| Secret | `ANDROID_KEYSTORE_BASE64` | Base64 do keystore permanente |
| Secret | `ANDROID_KEYSTORE_PASSWORD` | Senha do keystore |
| Secret | `ANDROID_KEY_ALIAS` | Alias da chave permanente |
| Secret | `ANDROID_KEY_PASSWORD` | Senha da chave |
| Variable pública | `ANDROID_SIGNING_CERT_SHA256` | Impressão SHA-256 aprovada, 64 caracteres hexadecimais, com ou sem `:` |

Use **Settings → Environments → android-distribution → Secrets/Variables**, ou a interface de Secrets/Variables do repositório. Pode-se transmitir o base64 diretamente à CLI autenticada sem gravá-lo em texto aberto:

```sh
base64 -w0 /caminho/privado/nfe-distribution.p12 | \
  gh secret set ANDROID_KEYSTORE_BASE64 --repo Pevicasi/Consulta-nota-fiscal --env android-distribution
```

Os outros Secrets devem ser inseridos nos prompts seguros de `gh secret set` ou na interface GitHub. Nunca coloque senhas em argumentos `--body`, arquivos versionados ou comandos exibidos em logs. Não altere a variável do certificado apenas para fazer um build inesperado passar: investigue a mudança de identidade.

Após provisionar e aprovar a identidade, execute **Actions → Android APK → Run workflow**, branch main, marcando **distribution**. O job exige regressão e testes instrumentados aprovados, verifica o certificado antes de compilar, executa também os testes no release assinado e verifica substituição do pacote no emulador. Confere assinatura APK v2, certificado fixado, pacote, versão, `debuggable=false` e checksum antes de publicar `nfe-facil-release-<commit>`. As Actions são fixadas por SHA de commit; o job de assinatura não salva cache Gradle. O keystore temporário fica apenas em RUNNER_TEMP, com diretório 0700/arquivo 0600, e é removido no fim, inclusive em falha. Nunca se gera uma nova chave de distribuição no workflow.

Os testes automatizados da política geram somente fixtures isoladas de dois dias de validade; elas não assinam nenhum APK publicado e não são chaves de distribuição.

## Migração única do APK antigo

1. **Não desinstale nem limpe dados antes de guardar o que precisa.** Preserve os XMLs originais fora do armazenamento privado do app, exporte os CSVs necessários pelo seletor Salvar como e registre chaves/histórico e configurações importantes em local seguro. CSV não substitui XML nem restaura a sessão completa.
2. A versão 1.0.1 não oferece exportação/restauração integral da sessão. Se depender desse estado e não tiver uma cópia verificável, interrompa a migração. Backup automático Android permanece desativado para evitar envio involuntário de dados fiscais. Não se promete recuperação de dados após desinstalar.
3. Confira pacote, versão e certificado dos APKs com `aapt dump badging` e `apksigner verify --print-certs`. O certificado deve coincidir para atualização direta. O release permanente deve ter versionCode maior que o instalado.
4. Sem a chave privada compatível com o APK debug anterior, não há atualização direta. Somente após backup conferido e decisão explícita do usuário, remova manualmente a versão antiga e instale o release aprovado. A desinstalação apaga os dados privados. Nenhum script deste projeto desinstala ou limpa dados do aparelho do usuário.
5. Reimporte XMLs e refaça as configurações necessárias. Atualizações seguintes devem usar **o mesmo keystore permanente**, a mesma identidade de pacote e versionCode crescente. A variável de certificado fixa essa identidade no CI.

O QA pode ser usado ao lado do app antigo enquanto a migração/assinatura não forem aprovadas. Seus dados não migram automaticamente entre os dois pacotes.

Referência oficial: https://developer.android.com/studio/publish/app-signing

Se futuramente adotar Play App Signing, diferencie a chave de envio da chave que efetivamente assina os APKs instalados. Compatibilidade entre APKs externos e a Play Store exige planejamento da identidade de assinatura pelo responsável; não se presume que as duas chaves sejam iguais.
