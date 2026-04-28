# Documentazione progetto: Liar's Table Online

## 1. Cos'e il progetto

Liar's Table Online e un gioco multiplayer realizzato in Java. Il progetto permette a piu giocatori di collegarsi allo stesso tavolo tramite rete locale o indirizzo IP, entrare in una lobby, dichiararsi pronti e giocare una partita basata su bluff, accuse e rischio.

Il gioco prende ispirazione dai giochi di carte in cui non conta solo la carta giocata, ma anche la capacita di mentire e capire quando gli altri stanno bluffando. Ogni giocatore riceve alcune carte, gioca carte coperte dichiarando che corrispondono alla carta bersaglio del round, e gli altri possono decidere se credergli o accusarlo.

Il progetto e diviso in due parti principali:

- un server, che gestisce la lobby, i giocatori, i turni, le carte, le accuse e le eliminazioni;
- un client grafico JavaFX, che permette al giocatore di collegarsi, vedere il tavolo, giocare carte, passare il turno, accusare un bluff e visualizzare gli eventi della partita.

## 2. Obiettivo del gioco

L'obiettivo e rimanere l'ultimo giocatore ancora in partita. Un giocatore viene eliminato quando perde nella fase di rischio chiamata nel codice "Risk Chamber", cioe una roulette russa simulata.

La partita termina quando rimane un solo giocatore non eliminato. In quel momento il server comunica a tutti il vincitore e il client mostra la schermata finale con alcune statistiche della partita.

## 3. Come si gioca

Per iniziare una partita servono almeno 2 giocatori. Il massimo previsto dal progetto e 4 giocatori.

Il flusso di gioco e questo:

1. Un giocatore avvia il server.
2. Ogni giocatore apre il client, inserisce IP, porta e nickname.
3. I giocatori entrano nella lobby.
4. Ogni giocatore preme il pulsante "Ready".
5. Quando tutti i giocatori sono pronti, la partita inizia automaticamente.
6. A ogni round il server sceglie una carta bersaglio: `KING` oppure `QUEEN`.
7. Ogni giocatore riceve 3 carte casuali.
8. Nel proprio turno un giocatore puo giocare una carta coperta, passare il turno oppure accusare l'ultima giocata.
9. Se viene fatta un'accusa, il server controlla se la carta giocata era vera o falsa.
10. Chi sbaglia subisce la Risk Chamber.
11. La partita continua finche resta un solo giocatore.

## 4. Regole del gioco

### Carte disponibili

Nel progetto esistono tre tipi di carte:

- `KING`
- `QUEEN`
- `JOKER`

All'inizio di ogni round la carta bersaglio puo essere `KING` o `QUEEN`. Il `JOKER` e considerato sempre valido, quindi funziona come carta jolly.

### Distribuzione delle carte

Ogni giocatore vivo riceve 3 carte a ogni nuovo round. Le carte vengono generate casualmente dal server:

- circa 40% di probabilita di ottenere `KING`;
- circa 40% di probabilita di ottenere `QUEEN`;
- circa 20% di probabilita di ottenere `JOKER`.

### Turno del giocatore

Quando e il proprio turno, il giocatore puo:

- giocare una carta dalla propria mano;
- passare il turno;
- accusare l'ultima carta giocata da un altro giocatore.

Quando un giocatore gioca una carta, la carta viene giocata coperta. Il messaggio visibile agli altri dice che il giocatore ha dichiarato la carta bersaglio del round, ma gli altri non vedono subito la carta reale.

### Accusa di bluff

Un giocatore puo accusare l'ultima carta giocata. Il server controlla la carta reale:

- se la carta giocata non corrisponde alla carta bersaglio e non e un `JOKER`, allora il giocatore accusato stava bluffando;
- se la carta giocata corrisponde alla carta bersaglio oppure e un `JOKER`, allora l'accusa era sbagliata.

Se il bluff viene scoperto, il giocatore che ha mentito entra nella Risk Chamber.

Se l'accusa e sbagliata, entra nella Risk Chamber il giocatore che ha accusato.

### Risk Chamber

La Risk Chamber e una penalita che simula una roulette russa. Ogni giocatore ha un cilindro con un certo numero di camere disponibili. All'inizio il cilindro ha 6 camere.

Quando un giocatore entra nella Risk Chamber:

- solo quel giocatore puo premere il pulsante "PULL TRIGGER";
- il server estrae casualmente una camera;
- se la camera corrisponde alla camera caricata, il giocatore viene eliminato;
- se sopravvive, il numero di camere rimanenti diminuisce, quindi il rischio futuro aumenta.

Nel codice ogni giocatore ha:

- `lives`, che indica se e ancora in gioco;
- `bulletsLoaded`, impostato a 1;
- `cylinderSlots`, che parte da 6 e diminuisce se il giocatore sopravvive alla Risk Chamber.

## 5. Come si usa il programma

### Requisiti

Per eseguire il progetto servono:

- Java 17 o superiore;
- Maven;
- connessione di rete locale se si gioca da computer diversi.

Il progetto usa JavaFX per l'interfaccia grafica. La dipendenza JavaFX e dichiarata nel file `pom.xml`.

### Avvio del server

Da terminale, nella cartella del progetto, si puo avviare il server con:

```bash
mvn compile
java -cp target/classes com.liarstable.server.LiarsTableServer
```

In alternativa, se si esegue da un IDE, bisogna lanciare la classe:

```text
com.liarstable.server.LiarsTableServer
```

Il server usa di default la porta `5000`. La porta viene definita nella classe `Protocol`.

### Avvio del client

Per avviare il client grafico:

```bash
mvn javafx:run
```

Oppure da IDE si puo eseguire la classe:

```text
com.liarstable.client.LiarsTableClientApp
```

Nella schermata iniziale bisogna inserire:

- IP del server: `127.0.0.1` se server e client sono sullo stesso computer;
- porta: normalmente `5000`;
- nickname del giocatore.

Dopo aver premuto "Join Table", il giocatore entra nella lobby. Quando tutti premono "Ready", la partita parte automaticamente.

### Giocare da piu computer

Se si gioca da computer diversi nella stessa rete:

1. un computer avvia il server;
2. gli altri client inseriscono l'indirizzo IP del computer che ospita il server;
3. tutti usano la stessa porta, di default `5000`.

## 6. Struttura tecnica del progetto

La struttura principale del progetto e:

```text
liars-table-online/
├── pom.xml
├── src/
│   └── main/
│       ├── java/
│       │   └── com/liarstable/
│       │       ├── client/
│       │       │   └── LiarsTableClientApp.java
│       │       ├── common/
│       │       │   └── Protocol.java
│       │       └── server/
│       │           ├── Card.java
│       │           ├── GameRoom.java
│       │           ├── LiarsTableServer.java
│       │           └── Player.java
│       └── resources/
│           └── casino.css
```

### `pom.xml`

Il file `pom.xml` configura il progetto Maven. Specifica:

- Java 17 come versione del linguaggio;
- JavaFX 21.0.2 come libreria grafica;
- il plugin `javafx-maven-plugin`, usato per avviare il client con `mvn javafx:run`.

### `Protocol.java`

Questa classe contiene elementi condivisi tra client e server.

Le sue responsabilita principali sono:

- definire la porta predefinita del server, cioe `5000`;
- leggere una porta alternativa dalla proprieta di sistema `liarstable.port`;
- dividere i messaggi di rete usando il carattere `;`.

Il metodo piu importante e:

```java
public static String[] split(String message) {
    return message == null ? new String[0] : message.split(";", -1);
}
```

Questo metodo permette a client e server di interpretare messaggi testuali come:

```text
JOIN;Mario
PLAY_CARD;1
STATE;1;KING;Mario;Mario:1,Luca:1;Mario:6,Luca:6;KING,JOKER,QUEEN;true
```

### `Card.java`

`Card` e un enum che rappresenta le carte disponibili nel gioco:

```java
KING, QUEEN, JOKER
```

Ogni carta ha una label testuale, usata nei messaggi e nell'interfaccia.

### `Player.java`

La classe `Player` rappresenta un giocatore connesso al server.

Contiene:

- il socket di rete;
- il lettore `BufferedReader` per ricevere messaggi dal client;
- il writer `PrintWriter` per inviare messaggi al client;
- il nickname;
- lo stato di pronto nella lobby;
- la mano di carte;
- i valori della Risk Chamber.

Il metodo:

```java
public void send(String msg) {
    out.println(msg);
}
```

serve per inviare un messaggio testuale al client.

### `LiarsTableServer.java`

Questa e la classe principale del server.

Il server:

- apre un `ServerSocket` sulla porta configurata;
- resta in ascolto di nuove connessioni;
- crea un thread separato per ogni client;
- interpreta i comandi ricevuti;
- chiama i metodi di `GameRoom` per aggiornare la partita.

I comandi principali ricevuti dal client sono:

- `JOIN;nome`
- `READY`
- `PLAY_CARD;indice`
- `CALL_BLUFF`
- `PASS`
- `PULL_TRIGGER`

Questa classe non contiene tutta la logica del gioco: il suo compito principale e gestire la comunicazione di rete e passare le azioni alla stanza di gioco.

### `GameRoom.java`

`GameRoom` e la classe piu importante per la logica della partita.

Gestisce:

- lista dei giocatori;
- ingresso in lobby;
- stato ready;
- inizio partita;
- creazione dei round;
- distribuzione delle carte;
- turno corrente;
- giocate;
- accuse;
- Risk Chamber;
- eliminazioni;
- fine partita.

Alcuni metodi importanti sono:

- `addPlayer(Player p)`: aggiunge un giocatore alla lobby;
- `setReady(Player p)`: imposta un giocatore come pronto;
- `startGame()`: inizializza la partita;
- `newRound()`: crea un nuovo round e distribuisce le carte;
- `playCard(Player p, int index)`: gestisce la giocata di una carta;
- `callBluff(Player accuser)`: controlla se un'accusa e corretta;
- `pullTrigger(Player p)`: risolve la Risk Chamber;
- `sendStateToAll()`: invia lo stato aggiornato a tutti i client.

I metodi pubblici sono dichiarati `synchronized`. Questo e importante perche il server usa un thread per ogni client: senza sincronizzazione, due giocatori potrebbero modificare contemporaneamente lo stato della partita.

### `LiarsTableClientApp.java`

Questa classe contiene il client grafico JavaFX.

Si occupa di:

- mostrare la schermata iniziale;
- connettersi al server tramite `Socket`;
- inviare comandi al server;
- ascoltare i messaggi del server in un thread separato;
- aggiornare l'interfaccia grafica;
- mostrare lobby, tavolo, mano del giocatore, storico eventi e schermata finale.

Le schermate principali sono:

- start screen, per IP, porta e nickname;
- lobby screen, per vedere i giocatori e premere "Ready";
- game screen, per giocare;
- final screen, per mostrare vincitore e statistiche.

Il metodo `listenServer()` riceve continuamente messaggi dal server. Siccome JavaFX permette di modificare l'interfaccia solo dal thread grafico, il codice usa:

```java
Platform.runLater(() -> handleMessage(msg));
```

In questo modo i messaggi di rete vengono trasformati in aggiornamenti grafici in modo corretto.

### `casino.css`

Il file `casino.css` contiene lo stile dell'interfaccia.

Definisce:

- sfondo scuro in stile casino;
- colori oro, rosso e verde;
- stile dei pulsanti;
- carte;
- tavolo;
- pannelli giocatore;
- log eventi;
- overlay della Risk Chamber;
- animazioni visive come evidenziazione, ombre e stati di eliminazione.

## 7. Protocollo di comunicazione client-server

Client e server comunicano tramite messaggi testuali inviati su socket TCP. Ogni messaggio e una riga di testo. I campi sono separati da `;`.

Esempi di messaggi inviati dal client:

```text
JOIN;Mario
READY
PLAY_CARD;0
CALL_BLUFF
PASS
PULL_TRIGGER
```

Esempi di messaggi inviati dal server:

```text
WELCOME;Inserisci nickname con JOIN;nome
LOBBY;Mario(ready),Luca
LOG;Nuovo round. Carta dichiarata: KING
STATE;1;KING;Mario;Mario:1,Luca:1;Mario:6,Luca:6;KING,QUEEN,JOKER;true
RESULT;Bluff caught: Mario revealed QUEEN
RISK;START;Mario;Bluff caught;6;true
RISK;RESULT;Mario;Bluff caught;6;5;3;false
GAME_OVER;Luca
```

Il vantaggio di questo protocollo e che e semplice da leggere e da debuggare. Lo svantaggio e che richiede attenzione: se un messaggio ha campi mancanti o valori non validi, bisogna gestire gli errori nel codice.

## 8. Funzionamento tecnico di una partita

Quando il server riceve `READY` da tutti i giocatori, `GameRoom` chiama `startGame()`.

`startGame()`:

- imposta la partita come iniziata;
- resetta round, rischio e dati dei giocatori;
- assegna a ogni giocatore 1 vita, 1 proiettile caricato e 6 camere;
- chiama `newRound()`.

`newRound()`:

- aumenta il numero del round;
- sceglie casualmente il target tra `KING` e `QUEEN`;
- svuota e ridistribuisce la mano dei giocatori vivi;
- invia lo stato aggiornato a tutti.

Quando un giocatore usa `PLAY_CARD`, il server:

- controlla che sia davvero il suo turno;
- controlla che l'indice della carta sia valido;
- rimuove la carta dalla mano;
- salva la carta come ultima carta giocata;
- passa il turno al giocatore vivo successivo;
- invia il nuovo stato.

Quando un giocatore usa `CALL_BLUFF`, il server:

- controlla che esista una carta giocata da accusare;
- impedisce al giocatore di accusare se stesso;
- controlla se la carta e vera;
- manda in Risk Chamber chi ha sbagliato.

Quando un giocatore usa `PULL_TRIGGER`, il server:

- verifica che sia il giocatore corretto;
- genera un numero casuale tra 1 e il numero di camere disponibili;
- elimina il giocatore se il numero rientra nella camera caricata;
- altrimenti riduce le camere disponibili;
- comunica il risultato a tutti;
- avvia un nuovo round o termina la partita.

## 9. Scelte tecniche

Il progetto usa un'architettura client-server classica:

- il server e l'autorita centrale della partita;
- i client non decidono autonomamente il risultato delle azioni;
- il client invia solo richieste;
- il server verifica le regole e invia lo stato aggiornato.

Questa scelta evita che ogni client abbia una versione diversa della partita. Tutti ricevono lo stesso stato da `GameRoom`.

L'interfaccia e separata dalla logica di gioco:

- la logica delle regole si trova nel package `server`;
- l'interfaccia grafica si trova nel package `client`;
- le costanti condivise stanno nel package `common`.

Questa separazione rende il progetto piu ordinato e piu facile da spiegare.

## 10. Possibili miglioramenti

Il progetto funziona, ma si potrebbe migliorare in vari modi:

- aumentare il numero di vite iniziali;
- aggiungere una schermata per creare piu stanze;
- salvare statistiche dei giocatori;
- gestire riconnessioni dopo disconnessione;
- aggiungere una chat;
- rendere il protocollo piu robusto usando JSON invece di stringhe separate da `;`;
- aggiungere test automatici per la logica di `GameRoom`;
- permettere di configurare il numero massimo di giocatori.

## 11. Conclusione

Liar's Table Online e un progetto Java completo che unisce programmazione di rete, interfaccia grafica e logica di gioco. Il server gestisce le regole e mantiene lo stato ufficiale della partita, mentre il client JavaFX offre un'interfaccia visuale per giocare.

Dal punto di vista tecnico, il progetto mostra l'uso di socket TCP, thread, classi condivise, enum, collezioni Java, sincronizzazione e JavaFX. Dal punto di vista del gameplay, propone una partita veloce basata su bluff, accuse e rischio progressivo.
