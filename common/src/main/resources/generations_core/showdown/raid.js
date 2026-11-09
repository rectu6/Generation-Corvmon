/*
 * Corvemon - Dynamax raid rules, run inside Cobblemon's Showdown (our own code).
 *
 * Showdown has no 4 against 1: each real player fights the boss in their own
 * singles battle (p1 against p2). The four participants of the raid are listed in
 * every battle, by slot:
 * - "local": this battle's player and Pokémon, fought by Showdown;
 * - "remote": another player, fought in their own battle and only shown here;
 * - "npc": an NPC ally, simulated by the host battle (the first one) and only shown
 *   in the others.
 * The server keeps what they share (boss HP, shield, faints) and copies it between
 * battles with GenerationsRaid.apply/state; it decides the boss's actions once per
 * turn with GenerationsRaid.plan, so every player sees the same raid.
 *
 * Rules (Sword and Shield, settings from config/generations/core/raids/):
 * - a fainted Pokémon sits out the next turn, then comes back with full HP, and its
 *   trainer may cheer the team meanwhile;
 * - no switching: each player brings one Pokémon;
 * - the boss may attack several times a turn, aims at any participant, and its
 *   moves that hit every foe hit them all;
 * - from 3 stars, a shield of segments goes up at set HP thresholds: damage is
 *   reduced, each hit breaks a segment (Max moves two);
 * - shockwaves cancel stat changes and abilities and cure the boss;
 * - the raid fails at the faint limit or the turn limit.
 * Lines "|gcraid|..." are read by RaidInstruction.kt.
 */
(function () {
	'use strict';

	const ID = 'generationsraid';
	const STATS = ['atk', 'def', 'spa', 'spd', 'spe'];
	// Abilities that make a Pokémon immune to a type: attackers avoid those moves.
	const ABSORBS = {
		levitate: 'Ground', eartheater: 'Ground', flashfire: 'Fire', wellbakedbody: 'Fire',
		waterabsorb: 'Water', stormdrain: 'Water', dryskin: 'Water', voltabsorb: 'Electric',
		lightningrod: 'Electric', motordrive: 'Electric', sapsipper: 'Grass',
	};
	// Move targets that hit every foe.
	const SPREAD = ['allAdjacentFoes', 'allAdjacent', 'all', 'foeSide'];

	function raidOf(battle) {
		return battle.generationsRaid;
	}

	function emit(battle, ...args) {
		battle.add('gcraid', ...args);
	}

	function event(raid, data) {
		raid.events.push(data);
	}

	function bySlot(raid, slot) {
		return raid.participants.find(p => p.slot === slot);
	}

	function localOf(raid) {
		return raid.participants.find(p => p.kind === 'local');
	}

	/** Participants this battle runs: its player, and the NPCs on the host. */
	function simulated(raid, p) {
		return p.kind === 'npc' && raid.host;
	}

	function up(raid) {
		return raid.participants.filter(p => p.down === null && !p.gone);
	}

	function hpOf(p) {
		return p.mon.hp;
	}

	/** Full status, for the raid HUD. */
	function sendState(battle) {
		const raid = raidOf(battle);
		emit(battle, 'state', JSON.stringify(stateOf(battle)));
	}

	function stateOf(battle) {
		const raid = raidOf(battle);
		return {
			turn: battle.turn,
			maxTurns: raid.cfg.maxTurns,
			faints: raid.faints,
			maxFaints: raid.cfg.maxFaints,
			bossHp: raid.boss.hp,
			bossMaxHp: raid.boss.maxhp,
			shield: { active: raid.shield.active, segments: raid.shield.segments, max: raid.shield.max, phase: raid.shield.phase },
			participants: raid.participants.map(p => ({ slot: p.slot, hp: hpOf(p), maxhp: p.mon.maxhp, down: p.down, gone: !!p.gone })),
			ended: battle.ended,
			winner: battle.ended ? (battle.winner === battle.p1.name ? 'players' : 'boss') : null,
			events: raid.events,
		};
	}

	function fail(battle, reason) {
		if (battle.ended) return;
		const raid = raidOf(battle);
		emit(battle, 'fail', reason);
		event(raid, { type: 'fail', reason });
		battle.win(battle.p2);
	}

	/** One more fainted participant: the raid fails at the limit (unless the boss is down too). */
	function countFaint(battle, p) {
		const raid = raidOf(battle);
		raid.faints++;
		emit(battle, 'down', 'slot' + p.slot, raid.faints);
		event(raid, { type: 'down', slot: p.slot });
		sendState(battle);
		if (raid.faints >= raid.cfg.maxFaints && raid.boss.hp > 0) fail(battle, 'faints');
	}

	function effectiveness(battle, move, target) {
		if (!target.runImmunity(move.type, false)) return -99;
		if (ABSORBS[target.ability] === move.type) return -99;
		return battle.dex.getEffectiveness(move, target);
	}

	function hits(battle, move) {
		return move.accuracy === true || battle.random(100) < move.accuracy;
	}

	function result(eff) {
		return eff > 0 ? 'super' : eff < 0 ? 'weak' : 'hit';
	}

	// ---- NPC allies -----------------------------------------------------------

	/** An NPC's move: often the strongest against the boss, sometimes another one. */
	function chooseAllyMove(battle, mon, boss) {
		const options = [];
		for (const slot of mon.moveSlots) {
			const move = battle.dex.moves.get(slot.id);
			if (!move.exists || move.category === 'Status' || move.ohko || move.damageCallback) continue;
			if (move.selfdestruct || move.flags['charge'] || move.flags['recharge']) continue;
			const eff = effectiveness(battle, move, boss);
			if (eff === -99) continue;
			const stab = mon.hasType(move.type) ? 1.5 : 1;
			options.push({ move, score: (move.basePower || 60) * stab * Math.pow(2, eff) });
		}
		if (!options.length) return null;
		options.sort((a, b) => b.score - a.score);
		return battle.randomChance(7, 10) ? options[0].move : battle.sample(options).move;
	}

	/** End of turn: each NPC that can fight attacks the boss. */
	function alliesAttack(battle) {
		const raid = raidOf(battle);
		const boss = raid.boss;
		for (const p of raid.participants) {
			if (!simulated(raid, p) || p.down !== null || battle.ended || boss.hp <= 0) continue;
			const move = chooseAllyMove(battle, p.mon, boss);
			if (!move) continue;
			const active = battle.dex.getActiveMove(move.id);
			if (!hits(battle, active)) {
				emit(battle, 'ally', p.slot, move.name, 'miss');
				continue;
			}
			const damage = battle.actions.getDamage(p.mon, boss, active, true);
			if (!damage) {
				emit(battle, 'ally', p.slot, move.name, 'immune');
				continue;
			}
			emit(battle, 'ally', p.slot, move.name, result(battle.dex.getEffectiveness(active, boss)));
			event(raid, { type: 'move', slot: p.slot, move: move.name });
			battle.damage(damage, boss, p.mon, active);
		}
	}

	/** The boss hits a simulated participant. */
	function hitSimulated(battle, boss, move, p, spread) {
		const raid = raidOf(battle);
		const active = battle.dex.getActiveMove(move.id);
		if (!hits(battle, active)) {
			emit(battle, 'boss', p.slot, move.name, 'miss');
			return;
		}
		let damage = battle.actions.getDamage(boss, p.mon, active, true);
		if (!damage) {
			emit(battle, 'boss', p.slot, move.name, 'immune');
			return;
		}
		if (spread) damage = Math.floor(damage * 3 / 4);
		damage = Math.max(1, Math.min(damage, p.mon.hp));
		p.mon.hp -= damage;
		emit(battle, 'boss', p.slot, move.name, result(battle.dex.getEffectiveness(active, p.mon)));
		if (p.mon.hp <= 0) {
			p.mon.hp = 0;
			p.down = battle.turn;
			countFaint(battle, p);
		} else {
			sendState(battle);
		}
	}

	/** The boss aims at a participant fought elsewhere: only told here. */
	function aimElsewhere(battle, move, p) {
		emit(battle, 'aim', p.slot, move.name);
	}

	// ---- Shield ---------------------------------------------------------------

	function raiseShield(battle) {
		const raid = raidOf(battle);
		const shield = raid.shield;
		shield.active = true;
		shield.segments = shield.max = raid.tier.shieldSegments;
		shield.lastMove = null;
		emit(battle, 'shield', shield.max);
		event(raid, { type: 'shield' });
		sendState(battle);
	}

	function breakSegments(battle, move) {
		const raid = raidOf(battle);
		const shield = raid.shield;
		const cfg = raid.cfg.shield;
		// One break per move unless set otherwise; misses never get here (no damage).
		if (!cfg.multiHitBreaksPerHit && shield.lastMove === move) return;
		shield.lastMove = move;
		const amount = move.isMax ? cfg.maxMoveSegments : cfg.segmentsPerHit;
		if (amount <= 0) return;
		shield.segments = Math.max(0, shield.segments - amount);
		emit(battle, 'shieldhit', shield.segments, shield.max);
		if (shield.segments === 0) {
			shield.active = false;
			shield.phase++;
			emit(battle, 'shieldbreak');
			event(raid, { type: 'shieldbreak' });
			if (cfg.breakDefenseDrop > 0) battle.boost({ def: -cfg.breakDefenseDrop, spd: -cfg.breakDefenseDrop }, raid.boss, null, null, true);
		}
		sendState(battle);
	}

	// ---- Extra attacks and shockwaves -----------------------------------------

	function runExtra(battle, extra) {
		const raid = raidOf(battle);
		const boss = raid.boss;
		const p = bySlot(raid, extra.target);
		const move = battle.dex.moves.get(extra.move);
		if (!p || !move.exists || boss.hp <= 0 || battle.ended || p.down !== null) return;
		emit(battle, 'extra', p.slot, move.name);
		const spread = SPREAD.includes(move.target);
		if (p.kind === 'local' || spread) {
			const local = localOf(raid);
			if (local && local.down === null && local.mon.isActive && !local.mon.fainted) {
				battle.actions.useMove(move.id, boss, local.mon);
			}
		}
		if (spread) {
			for (const other of raid.participants) if (simulated(raid, other) && other.down === null) hitSimulated(battle, boss, move, other, true);
		} else if (simulated(raid, p)) {
			hitSimulated(battle, boss, move, p, false);
		} else if (p.kind === 'remote' || p.kind === 'npc') {
			aimElsewhere(battle, move, p);
		}
	}

	function shockwave(battle) {
		const raid = raidOf(battle);
		const cfg = raid.cfg.shockwave;
		emit(battle, 'shockwave');
		event(raid, { type: 'shockwave' });
		const local = localOf(raid);
		if (local && local.down === null && local.mon.isActive && !local.mon.fainted) {
			if (cfg.clearBoosts) {
				local.mon.clearBoosts();
				battle.add('-clearboost', local.mon);
			}
			if (cfg.suppressAbilities && cfg.abilityTurns > 0 && !local.mon.volatiles['gastroacid']) {
				local.mon.addVolatile('gastroacid');
				raid.abilityUntil = battle.turn + cfg.abilityTurns;
			}
		}
		if (cfg.clearBoosts) for (const p of raid.participants) if (simulated(raid, p)) p.mon.clearBoosts();
		if (cfg.cureBossStatus && raid.boss.status) raid.boss.cureStatus();
	}

	/** The abilities taken by a shockwave come back after their turns. */
	function restoreAbility(battle) {
		const raid = raidOf(battle);
		const local = localOf(raid);
		if (raid.abilityUntil && battle.turn >= raid.abilityUntil) {
			raid.abilityUntil = 0;
			if (local && local.mon.volatiles['gastroacid']) local.mon.removeVolatile('gastroacid');
		}
	}

	// ---- Cheers ---------------------------------------------------------------

	function rollCheer(battle) {
		const c = raidOf(battle).cfg.cheers;
		const weights = [['boost', c.boostWeight], ['cure', c.cureWeight], ['heal', c.healWeight], ['nothing', c.nothingWeight]];
		let roll = battle.random() * weights.reduce((sum, w) => sum + w[1], 0);
		for (const [effect, weight] of weights) {
			roll -= weight;
			if (roll <= 0) return effect;
		}
		return 'nothing';
	}

	/** A cheer helps every participant still fighting, never the boss. */
	function applyCheer(battle, fromSlot, effect, stat) {
		const raid = raidOf(battle);
		const c = raid.cfg.cheers;
		emit(battle, 'cheer', fromSlot, effect, stat || '');
		for (const p of raid.participants) {
			if (p.down !== null || p.gone) continue;
			if (p.kind === 'local' && p.mon.isActive && !p.mon.fainted) {
				if (effect === 'boost') battle.boost({ [stat]: c.boostStages }, p.mon, null, null, true);
				else if (effect === 'cure' && p.mon.status) p.mon.cureStatus();
				else if (effect === 'heal') battle.heal(Math.max(1, Math.floor(p.mon.maxhp * c.healShare)), p.mon);
			} else if (simulated(raid, p)) {
				if (effect === 'boost') p.mon.boosts[stat] = Math.min(6, (p.mon.boosts[stat] || 0) + c.boostStages);
				else if (effect === 'cure') p.mon.status = '';
				else if (effect === 'heal') p.mon.hp = Math.min(p.mon.maxhp, p.mon.hp + Math.floor(p.mon.maxhp * c.healShare));
			}
		}
	}

	/** NPCs down this turn cheer on their own (on the host). */
	function npcCheers(battle) {
		const raid = raidOf(battle);
		if (!raid.cfg.cheers.npcCheer) return;
		for (const p of raid.participants) {
			if (!simulated(raid, p) || p.down === null || p.cheered === p.down) continue;
			p.cheered = p.down;
			const effect = rollCheer(battle);
			const stat = battle.sample(STATS);
			applyCheer(battle, p.slot, effect, stat);
			event(raid, { type: 'cheer', slot: p.slot, effect, stat });
		}
	}

	// ---- Revival --------------------------------------------------------------

	/** End of the turn after a faint: the participant comes back with full HP. */
	function revive(battle) {
		const raid = raidOf(battle);
		for (const p of raid.participants) {
			if (p.down === null || battle.turn <= p.down) continue;
			if (p.kind === 'local') {
				const pokemon = p.mon;
				p.down = null;
				pokemon.fainted = false;
				pokemon.faintQueued = false;
				pokemon.switchFlag = false;
				pokemon.status = '';
				pokemon.statusState = { id: '', target: pokemon, effectOrder: 0 };
				pokemon.hp = pokemon.maxhp;
				emit(battle, 'revive', 'slot' + p.slot);
				event(raid, { type: 'revive', slot: p.slot });
				// The slot is emptied so that the switch does not withdraw it first.
				pokemon.side.active[pokemon.position] = null;
				pokemon.isActive = false;
				battle.actions.switchIn(pokemon, pokemon.position);
			} else if (simulated(raid, p)) {
				p.down = null;
				p.mon.hp = p.mon.maxhp;
				emit(battle, 'revive', 'slot' + p.slot);
				event(raid, { type: 'revive', slot: p.slot });
			}
		}
	}

	// ---- Boss AI --------------------------------------------------------------

	/** How hard a move hits a participant (share of its HP, at most 1). */
	function threat(battle, boss, move, p) {
		if (move.category === 'Status') return 0.15;
		const active = battle.dex.getActiveMove(move.id);
		if (effectiveness(battle, active, p.mon) === -99) return 0;
		const damage = battle.actions.getDamage(boss, p.mon, active, true) || 0;
		return Math.min(1, damage / Math.max(1, hpOf(p)));
	}

	function usableMoves(boss) {
		return boss.moveSlots.filter(slot => slot.pp > 0 && !slot.disabled).map(slot => slot.id);
	}

	/**
	 * One action of the boss: with a high skill, the move and target that hurt most,
	 * else at random. Targets are weighted, never forced on one Pokémon.
	 */
	function chooseAction(battle, boss, candidates, avoid) {
		const raid = raidOf(battle);
		const moves = usableMoves(boss);
		if (!moves.length || !candidates.length) return null;
		const skill = raid.tier.aiSkill;
		const options = [];
		for (const id of moves) {
			const move = battle.dex.moves.get(id);
			for (const p of candidates) {
				let score = threat(battle, boss, move, p);
				if (p.slot === avoid) score *= 0.6;
				options.push({ move: id, target: p.slot, score });
			}
		}
		if (battle.random() < 0.2 + skill * 0.15) {
			// Weighted by score: strong options are likely, not certain.
			const total = options.reduce((sum, o) => sum + o.score + 0.05, 0);
			let roll = battle.random() * total;
			for (const o of options) {
				roll -= o.score + 0.05;
				if (roll <= 0) return { move: o.move, target: o.target };
			}
		}
		const o = battle.sample(options);
		return { move: o.move, target: o.target };
	}

	function lockedMove(boss) {
		if (boss.volatiles['mustrecharge']) return 'recharge';
		return boss.getLockedMove ? boss.getLockedMove() : null;
	}

	// ---- The field condition --------------------------------------------------

	const condition = {
		name: 'Generations Raid',
		effectType: 'Condition',

		// The local Pokémon faints: it sits out the next turn instead of losing.
		onFaint(pokemon) {
			const raid = raidOf(this);
			if (!raid || pokemon.side !== this.p1) return;
			const local = localOf(raid);
			if (!local) return;
			// Not a loss: Showdown has already counted it out.
			pokemon.side.pokemonLeft++;
			local.down = this.turn;
			countFaint(this, local);
		},

		// The boss aims where the turn's plan says.
		onBeforeMovePriority: -100,
		onBeforeMove(pokemon, target, move) {
			const raid = raidOf(this);
			if (!raid || pokemon !== raid.boss || SPREAD.includes(move.target)) return;
			if (move.category === 'Status' && (move.target === 'self' || move.target === 'allySide' || move.target === 'allies')) return;
			const p = raid.plan && raid.plan.target !== undefined ? bySlot(raid, raid.plan.target) : null;
			let aimed = p;
			if (!aimed || aimed.down !== null || aimed.gone) {
				const candidates = up(raid);
				if (!candidates.length) return;
				aimed = this.sample(candidates);
			}
			if (aimed.kind === 'local') return;
			if (simulated(raid, aimed) && move.category !== 'Status') hitSimulated(this, pokemon, move, aimed, false);
			else aimElsewhere(this, move, aimed);
			return false;
		},

		// Moves that hit every foe also hit the simulated participants; the local
		// player's move is told to the other battles.
		onAfterMove(pokemon, target, move) {
			const raid = raidOf(this);
			if (!raid) return;
			const local = localOf(raid);
			if (local && pokemon === local.mon) {
				event(raid, { type: 'move', slot: local.slot, move: move.name });
				return;
			}
			if (pokemon !== raid.boss || move.category === 'Status' || !SPREAD.includes(move.target)) return;
			for (const p of raid.participants) {
				if (simulated(raid, p) && p.down === null && !this.ended) hitSimulated(this, pokemon, move, p, true);
			}
		},

		// Shield: reduced damage, broken segment by segment; it rises at HP thresholds.
		onDamagePriority: -100,
		onDamage(damage, target, source, effect) {
			const raid = raidOf(this);
			if (!raid || target !== raid.boss || !effect || effect.effectType !== 'Move' || source === raid.boss) return;
			const shield = raid.shield;
			let result = damage;
			let rise = false;
			const thresholds = raid.cfg.shield.thresholds;
			if (!shield.active && raid.tier.shieldSegments > 0 && shield.phase < thresholds.length) {
				const limit = Math.ceil(thresholds[shield.phase] * target.maxhp);
				if (target.hp - result <= limit) {
					result = Math.max(0, target.hp - limit);
					rise = true;
				}
			}
			if (shield.active) {
				result = Math.max(1, Math.floor(result * (1 - raid.cfg.shield.damageReduction)));
				breakSegments(this, effect);
			}
			if (rise) raiseShield(this);
			return result;
		},

		// Status moves bounce off the shield; the boss never flinches nor falls to one-hit KO moves.
		onTryHit(target, source, move) {
			const raid = raidOf(this);
			if (!raid || target !== raid.boss) return;
			if (move.ohko) {
				this.add('-immune', target);
				return null;
			}
			if (raid.shield.active && raid.cfg.shield.blockStatusMoves && move.category === 'Status') {
				this.add('-fail', target);
				emit(this, 'shieldblock', move.name);
				return null;
			}
		},
		onTryAddVolatile(status, target) {
			const raid = raidOf(this);
			if (raid && target === raid.boss && status.id === 'flinch') return null;
		},

		onFieldResidualOrder: 999,
		onFieldResidual() {
			const raid = raidOf(this);
			if (!raid || this.ended) return;
			const plan = raid.plan || {};
			for (const extra of plan.extras || []) runExtra(this, extra);
			if (this.ended || raid.boss.hp <= 0) return;
			if (plan.shockwave) shockwave(this);
			alliesAttack(this);
			if (this.ended || raid.boss.hp <= 0) return;
			npcCheers(this);
			revive(this);
			restoreAbility(this);
			if (this.turn >= raid.cfg.maxTurns) {
				fail(this, 'turns');
				return;
			}
			raid.plan = null;
			sendState(this);
		},
	};

	function battleOf(id) {
		const stream = battleMap.get(id);
		return stream ? stream.battle : null;
	}

	globalThis.GenerationsRaid = {
		/**
		 * data: { localSlot, host, tier, cfg, participants: [{ slot, name, kind, packed? }] }
		 * tier: { hpMultiplier, shieldSegments, aiSkill, maxAttacks, extraAttackChance, shockwave }
		 * cfg: { maxTurns, maxFaints, shield, shockwave, cheers }
		 */
		start(battle, data) {
			const boss = battle.p2.active[0];
			const Pokemon = boss.constructor;
			const raid = {
				boss,
				host: !!data.host,
				tier: data.tier,
				cfg: data.cfg,
				faints: 0,
				plan: null,
				events: [],
				abilityUntil: 0,
				shield: { active: false, segments: 0, max: 0, phase: 0, lastMove: null },
				shockwaves: { used: 0, last: -99 },
				participants: data.participants.map(p => {
					const mon = p.kind === 'local' ? battle.p1.active[0] : new Pokemon(battle.getTeam({ team: p.packed })[0], battle.p1);
					if (p.kind !== 'local') mon.hp = mon.maxhp;
					return { slot: p.slot, name: p.name, kind: p.kind, mon, down: null, cheered: null, gone: false };
				}),
			};
			battle.generationsRaid = raid;

			// A raid boss has a much bigger HP pool, full: the players' bars start from it.
			boss.maxhp = boss.baseMaxhp = Math.floor(boss.maxhp * (data.tier.hpMultiplier || 1));
			boss.hp = boss.maxhp;
			battle.add('-sethp', boss, boss.getHealth, '[silent]');

			const Condition = battle.dex.conditions.get('reflect').constructor;
			battle.dex.conditions.conditionCache.set(ID, new Condition({ ...condition, id: ID }));
			battle.field.addPseudoWeather(ID);
			sendState(battle);
			battle.sendUpdates();
		},

		/**
		 * The boss's actions this turn (host battle): its move and target, extra
		 * attacks and whether a shockwave goes off.
		 */
		plan(battle) {
			const raid = raidOf(battle);
			if (!raid || battle.ended) return {};
			const boss = raid.boss;
			const candidates = up(raid);
			const plan = { extras: [], shockwave: false };
			const locked = lockedMove(boss);
			const main = chooseAction(battle, boss, candidates, -1);
			if (main) {
				plan.target = main.target;
				plan.move = locked || main.move;
			}
			for (let i = 1; i < raid.tier.maxAttacks; i++) {
				if (battle.random() >= raid.tier.extraAttackChance) continue;
				const extra = chooseAction(battle, boss, candidates, plan.target);
				if (extra) plan.extras.push(extra);
			}
			const sw = raid.tier.shockwave;
			if (sw && sw.enabled && raid.shockwaves.used < sw.maxUses && battle.turn - raid.shockwaves.last >= sw.cooldownTurns &&
				boss.hp <= boss.maxhp * sw.belowHealth && battle.random() < sw.chance) {
				plan.shockwave = true;
				raid.shockwaves.used++;
				raid.shockwaves.last = battle.turn;
			}
			return plan;
		},

		/** Copies the shared raid and the turn's plan into this battle before it plays. */
		apply(battle, shared, plan) {
			const raid = raidOf(battle);
			if (!raid || battle.ended) return;
			if (shared) {
				if (typeof shared.bossHp === 'number' && shared.bossHp !== raid.boss.hp && raid.boss.hp > 0) {
					raid.boss.hp = Math.max(0, Math.min(raid.boss.maxhp, shared.bossHp));
					battle.add('-sethp', raid.boss, raid.boss.getHealth, '[silent]');
				}
				if (shared.shield) {
					raid.shield.active = shared.shield.active;
					raid.shield.segments = shared.shield.segments;
					raid.shield.max = shared.shield.max;
					raid.shield.phase = shared.shield.phase;
				}
				if (typeof shared.faints === 'number') raid.faints = shared.faints;
				if (shared.shockwaves) raid.shockwaves = shared.shockwaves;
				for (const s of shared.participants || []) {
					const p = bySlot(raid, s.slot);
					if (!p || p.kind === 'local' || simulated(raid, p)) continue;
					p.mon.hp = s.hp;
					p.down = s.down;
					p.gone = s.gone;
				}
			}
			if (plan) raid.plan = plan;
			raid.events = [];
			sendState(battle);
			battle.sendUpdates();
		},

		/** What this battle holds of the shared raid, and what happened since apply. */
		state(battle) {
			const raid = raidOf(battle);
			if (!raid) return null;
			const state = stateOf(battle);
			state.shockwaves = raid.shockwaves;
			return state;
		},

		/** A cheer from a fainted participant's trainer, decided by the server. */
		cheer(battle, fromSlot, effect, stat) {
			const raid = raidOf(battle);
			if (!raid || battle.ended) return;
			applyCheer(battle, fromSlot, effect, stat);
			battle.sendUpdates();
		},

		/** A participant left the raid (disconnection). */
		leave(battle, slot) {
			const raid = raidOf(battle);
			const p = raid && bySlot(raid, slot);
			if (!p) return;
			p.gone = true;
			sendState(battle);
			battle.sendUpdates();
		},

		/** Whether Showdown still waits for this side's choice ('p1' or 'p2'). */
		waiting(battle, side) {
			const s = battle && battle[side];
			return !!(s && !battle.ended && s.activeRequest && !s.activeRequest.wait && !s.isChoiceDone());
		},

		/** The raid ended in another battle: the same end here. */
		finish(battle, result) {
			const raid = raidOf(battle);
			if (!raid || battle.ended) return;
			if (result === 'win') {
				raid.boss.hp = 0;
				battle.add('-sethp', raid.boss, raid.boss.getHealth, '[silent]');
				battle.faint(raid.boss);
				battle.faintMessages();
			} else {
				fail(battle, result || 'faints');
			}
			battle.sendUpdates();
		},

		battleOf,
	};
})();
