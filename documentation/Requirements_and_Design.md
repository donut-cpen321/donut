# Requirements and Design

## 1. Change History

| **Change Date**   | **Modified Sections** | **Rationale** |
| ----------------- | --------------------- | ------------- |
| _Nothing to show_ |

---

## 2. Project Description

Donut is an instant matchmaking and ranking app for people who play competitive sports but can't commit to a weekly league.

The first version supports **racket sports only**:

- Tennis
- Pickleball
- Badminton

Every sport supports **singles (1v1)** and **doubles (2v2)** from the start. Other sports (e.g. basketball) may be added later.

---

## 3. Requirements Specification

### **3.1. List of Features**

#### **Authentication**
User will be able to login using their google account.

#### **Live matchmaking**
User can initiate live match making and our
system will match people/teams based on their
sports/location/format preferences

#### **Schedule match**
User can schedule a match in a future time and
the open match will show up in the feed.

#### **Live game info update**
During an active game, game information like
time/rounds/score will be updated by one person and sync across all devices that joined this match.

#### **Sports specific ranking**
An ELO based sports specific ranking system to
see your skill level compared to others.

### **3.2. Use Case Diagram**


### **3.3. Actors Description**
1. **User**: a recreational racket-sport player sign up with google, who joins or accepts games, and enters match scores. All app features are available to this actor
2. **Google Login** authenticates players and provides the identity token our app its backend trusts
3. **Google Maps Platform** *(external service)*: supplies court locations and display on map for scheduled games.
4. **Firebase Cloud Messaging** *(external service)*: delivers match and game notifications to players' devices.
**OpenWeather** *(external service, optional feature)*: supplies forecast data shown for outdoor games.

### **3.4. Use Case Description**
- Use cases for feature 1: Live matchmaking
1. **Initiate Live match**: User click a button and the server place the player into a queue to be matched
2. **Matchmaking**: the system selects a set of players who overlap on sport, format, time, court and rating, then notifies all of them.
3. **Accept a Match**: each notified player accepts or declines; when all accept, the game is booked, and if anyone has not accepted 30 minutes before the start the match is cancelled for everyone.

- Use cases for feature 2: Schedule match
4. **Create a scheduled match**: User create a schedule match with sports type, format, court and start time.
5. **Feed for scheduled match**: A separate feed that shows all the upcoming scheduled games and relevant game information like other player's ELO rating, format, sports type and court information.
6. **Managing scheduled match** User can join a scheduled match in the feed, they can also leave the game if the game is not starting in 30 minutes. The owner of the scheduled game can also delete the game at anytime.
8. **Starting a scheduled match** User can confirm they are present within 30 minutes after the starting time of that game. A scheduled game can only be started if all players are confirmed present with 30 minutes after the starting time of the game, otherwise the game will be canceled automatically.

- Use cases for feature 3: Live game info update
9. **Update game information** The owner of this match can update score and round information using the app.
10. **Live game data syncing** Live game information like score and rounds will be updated across all devices that joined this match.

- Use case for feature 4: Sports specific ranking
11. **Sport specific leaderboard** There will be a leaderboard for each sport that ranks player by their ELO for that sport.
12. **ELO change calculation** After the game, ELO change for each player will be calculated and saved to the match summary.
13. **Update leaderboard** After each game when the final score is submitted to the server, the server will also apply the ELO change to the leaderboard.

### **3.5. Formal Use Case Specifications (5 Most Major Use Cases)**
<a name="uc1"></a>

#### Use Case 1: Matchmaking

**Description**: ...

**Primary actor(s)**: ... 
    
**Main success scenario**:
1. ...
2. ...

**Failure scenario(s)**:
- 1a. ...
    - 1a1. ...
    - 1a2. ...

- 1b. ...
    - 1b1. ...
    - 1b2. ...
                
- 2a. ...
    - 2a1. ...
    - 2a2. ...

...

<a name="uc2"></a>

#### Use Case 2: [WRITE_USE_CASE_2_NAME_HERE]
**Description**: ...

**Primary actor(s)**: ... 
    
**Main success scenario**:
1. ...
2. ...

**Failure scenario(s)**:
- 1a. ...
    - 1a1. ...
    - 1a2. ...

- 1b. ...
    - 1b1. ...
    - 1b2. ...
                
- 2a. ...
    - 2a1. ...
    - 2a2. ...



<a name="uc3"></a>

#### Use Case 3: ELO Change calculation
**Description**: Calculate ELO change for all players base on the match result.

**Primary actor(s)**: Player 
    
**Main success scenario**:
1. ...
2. ...

**Failure scenario(s)**:
- 1a. ...
    - 1a1. ...
    - 1a2. ...

- 1b. ...
    - 1b1. ...
    - 1b2. ...
                
- 2a. ...
    - 2a1. ...
    - 2a2. ...



### **3.6. Screen Mock-ups**


### **3.7. Non-Functional Requirements**
<a name="nfr1"></a>

1. **[WRITE_NAME_HERE]**
    - **Description**: ...
    - **Justification**: ...
2. ...

---

## 4. Designs Specification
### **4.1. Main Components**
1. **[WRITE_NAME_HERE]**
    - **Purpose**: ...
    - **Interfaces**: 
        1. ...
            - **Purpose**: ...
        2. ...
2. ...


### **4.2. Databases**
1. **[WRITE_NAME_HERE]**
    - **Purpose**: ...
2. ...


### **4.3. External Modules**
1. **[WRITE_NAME_HERE]** 
- **Purpose**: ...
2. ...


### **4.4. Frameworks and Libraries**
1. **[WRITE_NAME_HERE]**
    - **Purpose**: ...
    - **Reason**: ...
2. ...


### **4.5. Dependencies Diagram**


### **4.6. Use Case Sequence Diagram (5 Most Major Use Cases)**
1. [**[WRITE_NAME_HERE]**](#uc1)\
[SEQUENCE_DIAGRAM_HERE]
2. ...


### **4.7. Design of Non-Functional Requirements**
1. [**[WRITE_NAME_HERE]**](#nfr1)
    - **Implementation**: ...
2. ...
