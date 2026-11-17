# Simulation de l'obtention des 16 fragments Enigma (manifestations Éon).
# Lancer : python -I tools/eon_odds_sim.py
#
# Modèle (échantillons de 30 s, comme Ho-Oh et Mew) :
# - seul le temps d'exploration « admissible » fait avancer le compteur (joueur actif, qui se déplace,
#   biome et dimension permis, hors région en délai) ; une part `a` du temps d'exploration ne l'est pas
#   (océans, villages, retours sur ses pas, région récompensée...) ;
# - un tirage toutes les `interval` minutes admissibles, chance `p` (× `repeat` si le biome a donné un
#   fragment parmi les 4 derniers, ce qui arrive avec la probabilité `q_repeat`) ;
# - une manifestation dure 1,5 à 2,5 min (compteur arrêté) ; réussie avec la probabilité `s` ;
# - le compteur repart à zéro après chaque manifestation ;
# - après une réussite, il faut s'éloigner de `reward_distance` blocs (vitesse nette d'exploration
#   `net_speed` blocs/s, 2 = à pied en flânant) avant que le compteur reprenne.
import random
import statistics

FRAGMENTS = 16


def one_run(rng, interval, p, s, a, repeat, q_repeat, reward_distance, net_speed):
    t = 0.0  # minutes d'exploration active (tout compris)
    got = 0
    n_manifest = 0
    while got < FRAGMENTS:
        # temps mural pour accumuler `interval` minutes admissibles (échantillons de 30 s)
        need = int(round(interval * 2))
        samples = 0
        ok = 0
        while ok < need:
            samples += 1
            if rng.random() >= a:
                ok += 1
        t += samples * 0.5
        chance = p * (repeat if rng.random() < q_repeat else 1.0)
        if rng.random() < chance:
            n_manifest += 1
            t += rng.uniform(1.5, 2.5)
            if rng.random() < s:
                got += 1
                t += reward_distance / net_speed / 60.0
    return t / 60.0, n_manifest


def stats(label, runs=40000, seed=1, **kw):
    rng = random.Random(seed)
    hours = []
    manif = []
    for _ in range(runs):
        h, m = one_run(rng, **kw)
        hours.append(h)
        manif.append(m)
    hours.sort()
    q = lambda f: hours[int(f * (len(hours) - 1))]
    lt8 = sum(1 for h in hours if h < 8) / len(hours)
    gt14 = sum(1 for h in hours if h > 14) / len(hours)
    gt20 = sum(1 for h in hours if h > 20) / len(hours)
    per_hour = statistics.mean(manif) / statistics.mean(hours)
    print(f"{label:<44} moy {statistics.mean(hours):5.2f} h  méd {q(0.5):5.2f}  P10 {q(0.1):5.2f}  P90 {q(0.9):5.2f}"
          f"  <8h {lt8*100:4.1f} %  >14h {gt14*100:4.1f} %  >20h {gt20*100:4.1f} %  manif/h {per_hour:4.2f}")


if __name__ == "__main__":
    base = dict(interval=8.0, p=0.25, s=0.85, a=0.10, repeat=0.5, q_repeat=0.25, reward_distance=500, net_speed=2.0)
    print("Valeurs proposées (8 min, 25 %, région récompensée : 500 blocs), selon le joueur et la carte :")
    for s in (0.95, 0.85, 0.70):
        for a in (0.05, 0.10, 0.20):
            stats(f"  réussite {int(s*100)} %, temps non admissible {int(a*100)} %", **{**base, "s": s, "a": a})
    print("Sans malus de biome répété :")
    stats("  réussite 85 %, 10 %", **{**base, "repeat": 1.0})
    retained = {**base, "p": 0.33, "repeat": 0.6}
    print("Valeurs retenues (8 min, 33 %, biome répété × 0,6) :")
    for s, a in ((0.95, 0.05), (0.85, 0.10), (0.70, 0.20)):
        stats(f"  réussite {int(s*100)} %, temps non admissible {int(a*100)} %", **{**retained, "s": s, "a": a})
    print("Variantes :")
    stats("  8 min, 30 %", **{**base, "p": 0.30})
    stats("  8 min, 20 %", **{**base, "p": 0.20})
    stats("  7 min, 25 %", **{**base, "interval": 7.0})
    stats("  10 min, 25 %", **{**base, "interval": 10.0})
    stats("  à cheval / Pokémon monture (6 blocs/s nets)", **{**base, "net_speed": 6.0})
